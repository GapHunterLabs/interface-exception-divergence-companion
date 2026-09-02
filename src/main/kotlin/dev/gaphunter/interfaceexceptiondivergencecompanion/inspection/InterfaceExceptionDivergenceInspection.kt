package dev.gaphunter.interfaceexceptiondivergencecompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.interfaceexceptiondivergencecompanion.detect.InterfaceExceptionDivergenceFinder
import dev.gaphunter.interfaceexceptiondivergencecompanion.model.DivergenceHit
import dev.gaphunter.interfaceexceptiondivergencecompanion.review.ReviewPrompt

/**
 * Flags a call through an interface-typed reference, wrapped in a
 * broad silent `catch`, where real implementations of that interface
 * method diverge on which `RuntimeException` they throw directly --
 * see [InterfaceExceptionDivergenceFinder].
 */
class InterfaceExceptionDivergenceInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = InterfaceExceptionDivergenceFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.methodName}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: DivergenceHit): String =
        "Call to ${hit.interfaceName}.${hit.methodName}() is caught broadly and silently, but '${hit.throwingImplementationName}' " +
            "throws ${hit.missingExceptionSimpleName} while at least one other real implementation of ${hit.interfaceName} never does -- " +
            "a Liskov violation the interface type alone can't warn you about"
}
