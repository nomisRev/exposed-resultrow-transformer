package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.reportOnDeclaration
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.Name.identifier
import org.jetbrains.kotlin.psi.KtElement
import kotlin.script.experimental.dependencies.ScriptReport

val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
    get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

private val RESULT_ROW_CLASS_ID = ClassId(
    packageFqName = FqName("org.jetbrains.kotlin.compiler.plugin.template"),
    relativeClassName = FqName("ResultRow"),
    isLocal = false
)

val TABLE_CLASS_ID = ClassId(
    packageFqName = FqName("org.jetbrains.exposed.sql"),
    relativeClassName = FqName("Table"),
    isLocal = false
)

class MyCodeGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private val PREDICATE =
            LookupPredicate.create { annotated(MY_CODE_GENERATE_ANNOTATION) }
    }

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

            @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
            createTopLevelFunction(Key(owner, tableClassId), callableId, owner.defaultType()) {
                extensionReceiverType(RESULT_ROW_CLASS_ID.defaultType(emptyList()))
            }.symbol
        }
}
