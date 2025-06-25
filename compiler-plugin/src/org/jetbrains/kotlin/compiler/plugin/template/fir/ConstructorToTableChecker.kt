package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.compiler.plugin.template.Module
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.resolve.isSubclassOf
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.renderReadableWithFqNames
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FirCheckers(
    session: FirSession,
    private val module: Module,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers =
        object : DeclarationCheckers() {
            override val classCheckers: Set<FirClassChecker> = setOf(ConstructorToTableChecker(module))
        }
}

class ConstructorToTableChecker(
    private val module: Module,
) : FirClassChecker(MppCheckerKind.Common) {
    override fun check(
        declaration: FirClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val annotation =
            declaration.getAnnotationByClassId(ClassId.topLevel(module.classIds.annotation), context.session)
        if (annotation == null) return

        val annotationClass = annotation.getTableFromAnnotation(context)
        val superTypes = annotationClass?.resolvedSuperTypes.orEmpty()
        val isTable = annotationClass.isTable(context)

        if (!isTable) {
            val supers =
                if (superTypes.isEmpty()) {
                    "found no super types"
                } else {
                    superTypes.joinToString { it.renderReadableWithFqNames() }
                }
            reporter.reportOn(
                annotation.source,
                Errors.ANNOTATION_ARGUMENT_NOT_TABLE,
                supers,
                context,
            )
            return
        }

        val columns = annotationClass.declarationSymbols.filterIsInstance<FirPropertySymbol>()

        if (!declaration.status.isData) {
            reporter.reportOn(
                declaration.source,
                Errors.DATA_CLASS_EXPECTED,
                declaration.classId,
                context,
            )
        }

        validatedPropertiesAndColumns(declaration, context, columns, reporter, annotationClass)
    }

    private fun validatedPropertiesAndColumns(
        declaration: FirClass,
        context: CheckerContext,
        columns: List<FirPropertySymbol>,
        reporter: DiagnosticReporter,
        annotationClass: FirRegularClassSymbol,
    ) {
        declaration
            .primaryConstructorIfAny(context.session)
            ?.valueParameterSymbols
            .orEmpty()
            .forEach { parameter ->
                val column = columns.firstOrNull { it.name == parameter.name }
                if (column == null) {
                    module.logger.log { "Parameter ${parameter.name} not found in columns for ${declaration.classId}" }
                    reporter.reportOn(
                        parameter.source,
                        Errors.DATA_CLASS_PROPERTY_NOT_FOUND,
                        parameter.name.asString(),
                        annotationClass.name.asString(),
                        columns.map { it.name.asString() },
                        context,
                    )
                }
            }
    }

    @OptIn(ExperimentalContracts::class)
    private fun FirRegularClassSymbol?.isTable(context: CheckerContext): Boolean {
        contract { returns(true) implies (this@isTable != null) }
        return this?.isSubclassOf(
            module.classIds.table
                .defaultType()
                .lookupTag,
            context.session,
            isStrict = false,
            lookupInterfaces = false,
        ) == true
    }

    private fun FirAnnotation.getTableFromAnnotation(context: CheckerContext): FirRegularClassSymbol? =
        (
            argumentMapping.mapping.entries
                .firstOrNull()
                ?.value as? FirGetClassCall
        )?.argument
            ?.resolvedType
            ?.toRegularClassSymbol(context.session)
}
