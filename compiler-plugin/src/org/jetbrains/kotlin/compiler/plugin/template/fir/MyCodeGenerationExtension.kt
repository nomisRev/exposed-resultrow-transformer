package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.compiler.plugin.template.Module
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.Name.identifier

class MyCodeGenerationExtension(
    session: FirSession,
    private val module: Module
) : FirDeclarationGenerationExtension(session) {
    private val PREDICATE =
        LookupPredicate.create { annotated(module.classIds.annotation) }

    private val predicateBasedProvider = session.predicateBasedProvider
    private val matchedClasses by lazy {
        predicateBasedProvider.getSymbolsByPredicate(PREDICATE).filterIsInstance<FirRegularClassSymbol>()
    }

    class Key(val annotated: FirRegularClassSymbol, val tableClassId: ClassId) : GeneratedDeclarationKey()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    override fun getTopLevelCallableIds(): Set<CallableId> =
        matchedClasses.mapTo(mutableSetOf()) {
            val classId = it.classId
            val functionName = identifier("to${classId.shortClassName}")
            CallableId(packageName = classId.packageFqName, className = null, callableName = functionName)
        }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> =
        matchedClasses.map { owner ->
            @OptIn(UnresolvedExpressionTypeAccess::class)
            val tableClassId =
                (owner.annotations.first().argumentMapping.mapping.entries.first().value as FirGetClassCall).argumentList.arguments.first().coneTypeOrNull?.classId!!

            module.logger.log { "Found ${owner.classId} for Exposed Transformation generation with Table: $tableClassId" }

            @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
            createTopLevelFunction(Key(owner, tableClassId), callableId, owner.defaultType()) {
                extensionReceiverType(module.classIds.resultRow.defaultType(emptyList()))
            }.apply {
                containingClassForStaticMemberAttr = owner.toLookupTag()
                module.logger.log { "Generated FIR function ${render()} for class ${owner.classId}" }
            }.symbol
        }
}
