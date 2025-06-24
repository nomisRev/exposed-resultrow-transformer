package org.jetbrains.kotlin.compiler.plugin.template.fir

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
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.isSubclassOf
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.renderReadableWithFqNames
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId

class FirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val classCheckers: Set<FirClassChecker> = setOf(ConstructorToTableChecker)
    }
}

object ConstructorToTableChecker : FirClassChecker(MppCheckerKind.Common) {
    override fun check(declaration: FirClass, context: CheckerContext, reporter: DiagnosticReporter) {
        val annotation =
            declaration.getAnnotationByClassId(ClassId.topLevel(MY_CODE_GENERATE_ANNOTATION), context.session)
        if (annotation == null) return

        val annotationClass =
            (annotation.argumentMapping.mapping.entries.firstOrNull()?.value as? FirGetClassCall)
                ?.argument
                ?.resolvedType
                ?.toRegularClassSymbol(context.session)

        val superTypes = annotationClass?.resolvedSuperTypes.orEmpty()
        val isTable = annotationClass?.isSubclassOf(
            TABLE_CLASS_ID.defaultType(emptyList()).lookupTag, context.session,
            isStrict = false,
            lookupInterfaces = false
        ) == true
        if (!isTable) {
            val supers = if (superTypes.isEmpty()) "found no super types"
            else superTypes.joinToString { it.renderReadableWithFqNames() }
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
                context
            )
        }

        declaration.primaryConstructorIfAny(context.session)
            ?.valueParameterSymbols.orEmpty()
            .forEach { parameter ->
                val column = columns.firstOrNull { it.name == parameter.name }
                if (column == null) {
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
}