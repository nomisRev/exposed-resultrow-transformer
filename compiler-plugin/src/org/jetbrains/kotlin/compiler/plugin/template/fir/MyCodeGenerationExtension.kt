package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.compiler.plugin.template.Module
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

class MyCodeGenerationExtension(
    session: FirSession,
    private val module: Module,
) : FirDeclarationGenerationExtension(session) {
    private val predicate = LookupPredicate.create { annotated(module.classIds.annotation) }
    private val predicateBasedProvider = session.predicateBasedProvider
    private val matchedClasses by lazy {
        predicateBasedProvider.getSymbolsByPredicate(predicate).filterIsInstance<FirRegularClassSymbol>()
    }

    sealed class Key(
        val annotated: FirRegularClassSymbol,
        val tableClassId: ClassId,
    ) : GeneratedDeclarationKey()

    class SingleKey(
        annotated: FirRegularClassSymbol,
        tableClassId: ClassId,
    ) : Key(annotated, tableClassId)

    class IterableKey(
        annotated: FirRegularClassSymbol,
        tableClassId: ClassId,
    ) : Key(annotated, tableClassId)

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    fun state(): Map<CallableId, FirRegularClassSymbol> =
        matchedClasses
            .flatMap {
                val classId = it.classId
                val functionName = Name.identifier("to${classId.shortClassName}")
                val functionNames = Name.identifier("to${classId.shortClassName}s")
                listOf(
                    Pair(
                        CallableId(packageName = classId.packageFqName, className = null, callableName = functionName),
                        it,
                    ),
                    Pair(
                        CallableId(packageName = classId.packageFqName, className = null, callableName = functionNames),
                        it,
                    ),
                )
            }.toMap()

    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    override fun getTopLevelCallableIds(): Set<CallableId> = state().keys

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        module.logger.log { "Generating function $callableId" }
        val state = state()
        val owner = state[callableId]

        @OptIn(UnresolvedExpressionTypeAccess::class)
        val tableClassId =
            (
                owner
                    ?.annotations
                    ?.firstOrNull()
                    ?.argumentMapping
                    ?.mapping
                    ?.entries
                    ?.firstOrNull()
                    ?.value as? FirGetClassCall
            )?.argumentList
                ?.arguments
                ?.firstOrNull()
                ?.coneTypeOrNull
                ?.classId ?: return emptyList()

        return if (state.contains(callableId) && callableId.callableName.asString().endsWith("s")) {
            listOf(resultRowIterableFunction(owner, tableClassId, callableId))
        } else {
            listOf(resultRowFunction(owner, tableClassId, callableId))
        }
    }

    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    private fun MyCodeGenerationExtension.resultRowFunction(
        owner: FirRegularClassSymbol,
        tableClassId: ClassId,
        callableId: CallableId,
    ): FirNamedFunctionSymbol =
        createTopLevelFunction(SingleKey(owner, tableClassId), callableId, owner.defaultType()) {
            extensionReceiverType(module.classIds.resultRow.defaultType(emptyList()))
        }.apply {
            containingClassForStaticMemberAttr = owner.toLookupTag()
            module.logger.log { "Generated FIR function ${render()} for class ${owner.classId}" }
        }.symbol

    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    private fun MyCodeGenerationExtension.resultRowIterableFunction(
        owner: FirRegularClassSymbol,
        tableClassId: ClassId,
        callableId: CallableId,
    ): FirNamedFunctionSymbol {
        val returnType =
            ConeClassLikeTypeImpl(
                lookupTag = StandardClassIds.Iterable.toLookupTag(),
                typeArguments = arrayOf(owner.defaultType()),
                isMarkedNullable = false,
            )

        return createTopLevelFunction(IterableKey(owner, tableClassId), callableId, returnType) {
            extensionReceiverType(
                ConeClassLikeTypeImpl(
                    lookupTag = StandardClassIds.Iterable.toLookupTag(),
                    typeArguments = arrayOf(module.classIds.resultRow.defaultType()),
                    isMarkedNullable = false,
                ),
            )
        }.apply {
            containingClassForStaticMemberAttr = owner.toLookupTag()
            module.logger.log { "Generated FIR function ${render()} for class ${owner.classId}" }
        }.symbol
    }
}

fun ClassId.defaultType(): ConeClassLikeType = defaultType(emptyList())
