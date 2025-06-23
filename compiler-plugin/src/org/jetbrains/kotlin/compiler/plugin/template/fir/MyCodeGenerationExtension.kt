package org.jetbrains.kotlin.compiler.plugin.template.fir
// myplugin/plugin/MyCodeGenerationExtension.kt

//import org.jetbrains.kotlin.fir.declarations.builder.buildClass
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
//import org.jetbrains.kotlin.fir.extensions.createClassPredicate
//import org.jetbrains.kotlin.fir.extensions.createFunctionPredicate
//import org.jetbrains.kotlin.fir.extensions.createPropertyPredicate
//import org.jetbrains.kotlin.fir.extensions.predicate.AnnotatedWith
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
//import org.jetbrains.kotlin.fir.resolve.to
//import org.jetbrains.kotlin.fir.resolve.transformers.StatusUpdatingTransformer
import org.jetbrains.kotlin.fir.symbols.impl.*
//import org.jetbrains.kotlin.fir.types.impl.FirImplicitTypeRef
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
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.types.ConstantValueKind


class MyCodeGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    // The FQN of your annotation
    private val MY_CODE_GENERATE_ANNOTATION: AnnotationFqn
        get() = FqName("org.jetbrains.kotlin.compiler.plugin.template.SomeAnnotation")

    private val classId
        get() = ClassId.topLevel(MY_CODE_GENERATE_ANNOTATION)

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

        val function = buildSimpleFunction {
            source = owner.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            returnTypeRef = owner.defaultType().toFirResolvedTypeRef()
            name = callableId.callableName
            symbol = FirNamedFunctionSymbol(callableId)
            status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
            resolvePhase = FirResolvePhase.RAW_FIR

            body = buildBlock {
                statements += buildLiteralExpression(
                    source = null, // TODO ??
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

// 2. Decide what class IDs might be generated
//    override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeDeclaration? {
// Example: Generate a "GeneratedData" class next to the annotated class
// This method is called to check if your plugin wants to provide a specific ClassId.
// You'll likely need a mechanism to map your generated class IDs.
// For simplicity, let's assume we generate a nested class named 'GeneratedData'
// inside classes annotated with @MyCodeGenerate.
//        val originalClassId = classId.parentClassId
//        if (originalClassId != null && classId.shortClassName.asString() == "GeneratedData") {
//            val originalClass = session.firProvider.getFirClassifierByFqName(originalClassId) as? FirRegularClass
//            if (originalClass != null && originalClass.annotations.any { it.toAnnotationClassId(session) == classId }) {
//                return generateGeneratedDataClass(classId)
//            }
//        }
//        return null
//    }

//    private fun generateGeneratedDataClass(classId: ClassId): FirRegularClass {
//        return buildClass {
//            moduleData = session.moduleData
//            resolvePhase = FirResolvePhase.STATUS
//            origin = FirDeclarationOrigin.Plugin
//            classKind = ClassKind.CLASS
//            scopeProvider = session.firDependenciesSymbolProvider.getClassDeclaredMemberScopeProvider()
//            status = FirResolvedDeclarationStatusImpl(
//                Visibilities.Public,
//                Modality.FINAL,
//                EffectiveVisibility.Public
//            ).apply {
//                isData = true // Make it a data class if you want
//            }
//            name = classId.shortClassName
//            symbol = FirRegularClassSymbol(classId)
//            // Add a primary constructor
//            declarations += buildConstructor {
//                moduleData = session.moduleData
//                resolvePhase = FirResolvePhase.STATUS
//                origin = FirDeclarationOrigin.Plugin
//                returnTypeRef = buildResolvedTypeRef { type = classId.toTypeProjection(session) }
//                status = FirResolvedDeclarationStatusImpl(
//                    Visibilities.Public,
//                    Modality.FINAL,
//                    EffectiveVisibility.Public
//                )
//                symbol = FirConstructorSymbol(classId.createNestedClassId(SpecialNames.INIT))
//                // Add parameters to the constructor if needed
//                valueParameters += buildValueParameter {
//                    moduleData = session.moduleData
//                    resolvePhase = FirResolvePhase.STATUS
//                    origin = FirDeclarationOrigin.Plugin
//                    returnTypeRef = buildResolvedTypeRef { type = session.builtinTypes.stringType.type }
//                    name = Name.identifier("value")
//                    symbol = FirValueParameterSymbol(CallableId(classId, Name.identifier("value")))
//                    isCrossinline = false
//                    isNoinline = false
//                    isVararg = false
//                    isImplicitlyNotNull = true
//                }
//            }
//            // Add properties
//            declarations += buildProperty {
//                moduleData = session.moduleData
//                resolvePhase = FirResolvePhase.STATUS
//                origin = FirDeclarationOrigin.Plugin
//                returnTypeRef = buildResolvedTypeRef { type = session.builtinTypes.stringType.type }
//                name = Name.identifier("value")
//                symbol = FirPropertySymbol(CallableId(classId, Name.identifier("value")))
//                status = FirResolvedDeclarationStatusImpl(
//                    Visibilities.Public,
//                    Modality.FINAL,
//                    EffectiveVisibility.Public
//                )
//                isVal = true
//                isVar = false
//                initializer = null // Data class property, will be initialized via constructor
//            }
//        }.also {
//            // Important: Transform the status of the generated class
//            StatusUpdatingTransformer(session).transformElement(it, null)
//        }
//    }


// 3. Generate members for an annotated class
//        val declarations = mutableListOf<FirDeclaration>()
//
//        // Check if the class is annotated with @MyCodeGenerate
//        val isAnnotated = klass.annotations.any {
//            it.classId?.asSingleFqName() == MY_CODE_GENERATE_ANNOTATION
//        }
//
//        if (isAnnotated) {
//            // Example: Generate a `create()` function in the companion object
//            // or directly in the class if it's not a companion.
//            // For simplicity, let's generate a simple `foo()` function
//            // directly in the annotated class.
//            declarations += buildSimpleFunction(
//                session,
//                klass.symbol.classId.createNestedClassId(Name.identifier("foo")), // CallableId relative to the class
//                Name.identifier("foo"),
//                session.builtinTypes.stringType.type // Return type
//            ) {
//                modality = Modality.PUBLIC
//                visibility = Visibilities.Public
//            }
//        }
//        return declarations
//    }
//
// Helper to build a simple function
//    private fun buildSimpleFunction(
//        session: FirSession,
//        callableId: CallableId,
//        name: Name,
//        returnType: ConeKotlinType,
//        configure: (FirFunctionBuilder.() -> Unit)? = null
//    ): FirSimpleFunction {
//        return buildSimpleFunction {
//            moduleData = session.moduleData
//            resolvePhase = FirResolvePhase.STATUS
//            origin = FirDeclarationOrigin.Plugin
//            returnTypeRef = buildResolvedTypeRef { type = returnType }
//            this.name = name
//            symbol = FirNamedFunctionSymbol(callableId)
//            status = FirResolvedDeclarationStatusImpl(
//                Visibilities.Public,
//                Modality.FINAL,
//                EffectiveVisibility.Public
//            )
//            configure?.invoke(this)
//        }
//    }

// 4. Generate nested classes for an annotated class (e.g., if you generate an inner builder)
//    override fun generateNestedClassLikeDeclaration(
//        owner: FirClass,
//        name: Name,
//        context: NestedClassGenerationContext
//    ): FirClassLikeDeclaration? {
//        // If the owner is annotated and the name matches what we want to generate,
//        // then generate the nested class.
//        val isAnnotated = owner.annotations.any { it.classId?.asSingleFqName() == MY_CODE_GENERATE_ANNOTATION }
//        if (isAnnotated && name.asString() == "GeneratedNestedClass") {
//            val classId = owner.symbol.classId.createNestedClassId(name)
//            return buildClass {
//                moduleData = session.moduleData
//                resolvePhase = FirResolvePhase.STATUS
//                origin = FirDeclarationOrigin.Plugin
//                classKind = ClassKind.CLASS
//                scopeProvider = session.firDependenciesSymbolProvider.getClassDeclaredMemberScopeProvider()
//                status = FirResolvedDeclarationStatusImpl(
//                    Visibilities.Public,
//                    Modality.FINAL,
//                    EffectiveVisibility.Public
//                )
//                this.name = name
//                symbol = FirRegularClassSymbol(classId)
//                // ... add members to GeneratedNestedClass ...
//            }
//        }
//        return null
//    }


}