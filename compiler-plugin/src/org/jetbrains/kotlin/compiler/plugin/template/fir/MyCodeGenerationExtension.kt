package org.jetbrains.kotlin.compiler.plugin.template.fir
// myplugin/plugin/MyCodeGenerationExtension.kt

//import org.jetbrains.kotlin.fir.declarations.builder.buildClass
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities

import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.declarations.builder.buildReceiverParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType


// The FQN of your annotation
private val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
    get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

private val RESULT_ROW_CLASS_ID = ClassId(
    packageFqName = FqName("org.jetbrains.exposed.sql"),
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

    // Key for generated declarations
    object Key : GeneratedDeclarationKey()

    init {
        println("MyCodeGenerationExtension")
    }

    // 1. Register predicates for what you're interested in generating based on
    //     This predicate tells the compiler that we might generate members for classes
    //     that are annotated with @MyCodeGenerate.
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        println("registerPredicates()")
        register(PREDICATE)
        println("Registered predicate: $PREDICATE")
    }

    private val annotatedClasses: MutableList<FirClassSymbol<*>> = mutableListOf()

    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    override fun getTopLevelCallableIds(): Set<CallableId> {
        println("getTopLevelCallableIds() called")
        val newGeneratedIds = matchedClasses.mapTo(mutableSetOf()) { it.callableId() }
        println("getTopLevelCallableIds returning: $newGeneratedIds")
        return newGeneratedIds
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        println("Generating function for callableId: $callableId, context: MemberGenerationContext?: $context") // Debug log
//        val owner = annotatedClasses.firstOrNull { it.callableId() == callableId }
//        if (owner == null) {
//            println("CallableId not found in generatedCallableIds") // Debug log
//            return emptyList()
//        }
        val functions = matchedClasses.map { owner ->
            val annotation =
                owner.annotations.first().argumentMapping.mapping.entries.first().value
            val classSymbol = (annotation as FirGetClassCall).argument.resolvedType.toRegularClassSymbol(session)
            val columns = classSymbol?.declarationSymbols.orEmpty()

            val paramToColumn = owner.primaryConstructorSymbol(session)?.valueParameterSymbols
                ?.groupBy { parameter ->
                    columns.single { (it as? FirPropertySymbol)?.name == parameter.name }
                }

            buildSimpleFunction {
                // Don't use the owner's source, create a new one
                source = null
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.Plugin(Key)
                symbol = FirNamedFunctionSymbol(callableId)
//            dispatchReceiverType = RESULT_ROW_CLASS_ID.defaultType(emptyList())
                receiverParameter = buildReceiverParameter {
                    source = null
                    resolvePhase = FirResolvePhase.RAW_FIR
                    origin = FirDeclarationOrigin.Plugin(Key)
                    moduleData = session.moduleData
                    typeRef = RESULT_ROW_CLASS_ID.defaultType(emptyList()).toFirResolvedTypeRef()
                    symbol = FirReceiverParameterSymbol()
                    containingDeclarationSymbol = this@buildSimpleFunction.symbol
                }

                returnTypeRef = session.builtinTypes.stringType.coneType.toFirResolvedTypeRef()
//            returnTypeRef = owner.defaultType().toFirResolvedTypeRef()
                name = callableId.callableName
                status =
                    FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
                resolvePhase = FirResolvePhase.RAW_FIR

                body = buildBlock {
                    statements += buildLiteralExpression(
                        source = null,
                        kind = ConstantValueKind.String,
                        value = "OK",
                        setType = true
                    )
                }
            }
        }


        println("Generated functions: ${functions.joinToString { it.symbol.toString() }}") // Debug log
        return functions.map { it.symbol }
    }

    fun FirClassSymbol<*>.callableId(): CallableId {
        val packageName = classId.packageFqName
        val functionName = Name.identifier("to${classId.shortClassName}")
        return CallableId(packageName = packageName, className = null, callableName = functionName)
    }

    override fun hasPackage(packageFqName: FqName): Boolean {
        println("hasPackage(packageFqName=$packageFqName)")
        return packageFqName == MY_CODE_GENERATE_ANNOTATION.parent() ||
                packageFqName == FqName("foo.bar") ||
                packageFqName == FqName("my.test.toUser") ||
                packageFqName == FqName.ROOT
    }
}