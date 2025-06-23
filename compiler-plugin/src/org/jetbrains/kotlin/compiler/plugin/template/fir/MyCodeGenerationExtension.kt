package org.jetbrains.kotlin.compiler.plugin.template.fir
// myplugin/plugin/MyCodeGenerationExtension.kt

//import org.jetbrains.kotlin.fir.declarations.builder.buildClass
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fakeElement

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
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType


class MyCodeGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    // The FQN of your annotation
    private val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
        get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

    private val classId
        get() = ClassId.topLevel(MY_CODE_GENERATE_ANNOTATION)

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
        val predicate = DeclarationPredicate.AnnotatedWith(setOf(MY_CODE_GENERATE_ANNOTATION))
        register(predicate)
        println("Registered predicate: $predicate")
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        println("generateNestedClassLikeDeclaration(owner=$owner, name=$name)")
        return super.generateNestedClassLikeDeclaration(owner, name, context)
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        println("generateConstructors(context=$context)")
        return super.generateConstructors(context)
    }

    // 3
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        println("generateProperties(callableId=$callableId, context=$context)")
        return super.generateProperties(callableId, context)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        println("generateTopLevelClassLikeDeclaration(classId=$classId)")
        return super.generateTopLevelClassLikeDeclaration(classId)
    }


    // 2
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        println("getCallableNamesForClass(classSymbol=$classSymbol)")
        return super.getCallableNamesForClass(classSymbol, context)
    }

    private var callableIds: MutableMap<CallableId, FirClassSymbol<*>> = mutableMapOf()

    private fun getTableColumns(tableClass: Any): List<String> {
        // TODO: Extract column information from Table class
        // This requires resolving the actual Table class and its columns
        return emptyList() // Placeholder implementation
    }

    // 1
    // TODO can be ued to generate getTopLevelCallableIds
    //    `context.owner.classId`
    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        context
        println("getNestedClassifiersNames(classSymbol=$classSymbol)")
        val packageName = context.owner.packageFqName()
        val functionName = Name.identifier("to${context.owner.classId.shortClassName}")
        callableIds[CallableId(packageName, null, functionName)] = context.owner
        println("Added callableId: ${CallableId(packageName, null, functionName)}")
        return super.getNestedClassifiersNames(classSymbol, context)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelClassIds(): Set<ClassId> {
        println("getTopLevelClassIds()")
        return super.getTopLevelClassIds()
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        println("Generating function for callableId: $callableId") // Debug log

        val owner = callableIds.getOrElse(callableId) { null }
        if (owner == null) {
            println("CallableId not found in generatedCallableIds") // Debug log
            return emptyList()
        }


        val annotation =
            owner.annotations.first().argumentMapping.mapping.entries.first().value

        val classSymbol = (annotation as FirGetClassCall).argument.resolvedType.toRegularClassSymbol(session)
        val columns = classSymbol?.declarationSymbols



        val function = buildSimpleFunction {
            // Don't use the owner's source, create a new one
            source = null
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            returnTypeRef = session.builtinTypes.stringType.coneType.toFirResolvedTypeRef()
//            returnTypeRef = owner.defaultType().toFirResolvedTypeRef()
            name = callableId.callableName
            symbol = FirNamedFunctionSymbol(callableId)
            status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
            resolvePhase = FirResolvePhase.RAW_FIR

            body = buildBlock {
                statements += buildLiteralExpression(
                    source = null, // Use null for the source element to avoid line number mapping issues
                    kind = ConstantValueKind.String,
                    value = "Hello, world!",
                    setType = true
                )
            }
        }

        println("Generated function: ${function.symbol}") // Debug log
        return listOf(function.symbol)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelCallableIds(): Set<CallableId> = callableIds.keys

    override fun hasPackage(packageFqName: FqName): Boolean {
        println("hasPackage(packageFqName=$packageFqName)")
        return packageFqName == MY_CODE_GENERATE_ANNOTATION.parent() ||
                packageFqName == FqName("foo.bar") ||
                packageFqName == FqName.ROOT
    }
}