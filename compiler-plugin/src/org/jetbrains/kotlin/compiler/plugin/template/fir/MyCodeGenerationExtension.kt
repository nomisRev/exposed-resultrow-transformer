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
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.Name.identifier

private val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
    get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

private val RESULT_ROW_CLASS_ID = ClassId(
    packageFqName = FqName("org.jetbrains.kotlin.compiler.plugin.template"),
    relativeClassName = FqName("ResultRow"),
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

    class Key(val mappings: Map<FirBasedSymbol<*>, FirValueParameterSymbol>) : GeneratedDeclarationKey()

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
            val annotation =
                owner.annotations.first().argumentMapping.mapping.entries.first().value
            val classSymbol = (annotation as FirGetClassCall).argument.resolvedType.toRegularClassSymbol(session)
            val columns = classSymbol?.declarationSymbols.orEmpty()

            val paramToColumn: Map<FirBasedSymbol<*>, FirValueParameterSymbol>? =
                owner.primaryConstructorSymbol(session)?.valueParameterSymbols
                    ?.associate { parameter ->
                        Pair(columns.single { (it as? FirPropertySymbol)?.name == parameter.name }, parameter)
                    }

            require(paramToColumn?.isNotEmpty() == true)

            @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
            createTopLevelFunction(
                Key(paramToColumn),
                callableId,
                session.builtinTypes.stringType.coneType, // owner.defaultType().toFirResolvedTypeRef()
            ) {
                extensionReceiverType(RESULT_ROW_CLASS_ID.defaultType(emptyList()))
            }.symbol
        }

    override fun hasPackage(packageFqName: FqName): Boolean {
        println("hasPackage(packageFqName=$packageFqName)")
        return packageFqName == MY_CODE_GENERATE_ANNOTATION.parent() ||
                packageFqName == FqName("foo.bar") ||
                packageFqName == FqName("my.test.toUser") ||
                packageFqName == FqName.ROOT
    }
}
