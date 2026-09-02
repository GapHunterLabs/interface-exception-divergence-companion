package dev.gaphunter.interfaceexceptiondivergencecompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.interfaceexceptiondivergencecompanion.model.DivergenceHit

/**
 * Real Class Hierarchy Analysis (CHA) -- the OPPOSITE of every other
 * whole-project mechanism in this catalog, which always DISCARDS a
 * call site the moment more than one real implementation exists
 * ("ambiguity never guessed"). Here, having 2+ real implementations is
 * the PRECONDITION: for a call site through an interface-typed
 * reference, resolves EVERY real, concrete implementation of the
 * interface in the project (`ClassInheritorsSearch`, the same
 * mechanism a compiler/JIT's own devirtualization decision would use),
 * computes each one's own directly-thrown exception NAMES, and flags a
 * call wrapped in a broad, silently-swallowing `catch` when at least
 * one implementation throws an exception at least one OTHER
 * implementation never does -- the caller was written assuming one
 * implementation's behavior, but the static (interface) type only
 * guarantees the interface's own contract.
 *
 * **Deliberately by simple NAME, never resolved against the real
 * JDK/classpath** -- both the thrown-exception identity and the catch
 * clause's type are read as plain reference text, same "simple name,
 * no resolution" discipline `unsafe-deserialization-sink-companion`
 * already uses for `ObjectInputStream` (that plugin's own doc:
 * checking `classRef.referenceName` rather than a resolved type keeps
 * detection working even when full JDK resolution is unavailable).
 * Confirmed necessary the hard way while building this plugin's own
 * test suite: an earlier version resolved the thrown exception against
 * `java.lang.RuntimeException` via `InheritanceUtil.isInheritor`, which
 * silently found nothing whenever the light test fixture's JDK mock
 * left `RuntimeException` itself unresolved -- Java's own rule that an
 * exception thrown from an override without being declared in the
 * interface method's `throws` clause MUST already be unchecked makes
 * the inheritance check redundant here anyway, not just a workaround.
 *
 * **v0.1 scope, stated honestly:** only exceptions thrown DIRECTLY
 * (`throw new X(...)`) in an implementation's own override body --
 * never follows a call to another method to infer transitive
 * exceptions (that's `exception-escape-chain-companion`'s own,
 * separate, already-published mechanism); a lambda/anonymous class
 * body nested inside the override is NOT descended into (a separate
 * execution context); only interfaces with 2-10 real implementations
 * in the project; the catch type must be exactly `Exception` or
 * `RuntimeException` by simple name (a genuinely broad catch) -- a
 * narrower catch of a specific exception type is a different, more
 * deliberate case, out of scope.
 */
object InterfaceExceptionDivergenceFinder {

    private const val MIN_IMPLEMENTATIONS = 2
    private const val MAX_IMPLEMENTATIONS = 10

    private val BROAD_CATCH_SIMPLE_NAMES = setOf("Exception", "RuntimeException")
    private val LOGGING_METHOD_NAMES = setOf("info", "warn", "error", "debug", "trace", "log", "println", "printstacktrace")

    fun findAll(file: PsiFile): List<DivergenceHit> {
        val hits = mutableListOf<DivergenceHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                hitForCall(call)?.let { hits += it }
            }
        })
        return hits
    }

    private fun hitForCall(call: PsiMethodCallExpression): DivergenceHit? {
        val qualifier = call.methodExpression.qualifierExpression ?: return null
        val interfaceClass = (qualifier.type as? PsiClassType)?.resolve() ?: return null
        if (!interfaceClass.isInterface) return null

        val interfaceMethod = call.resolveMethod() ?: return null
        if (interfaceMethod.containingClass != interfaceClass) return null // resolved elsewhere (e.g. Object) -- not this interface's own contract

        if (!isWrappedInSilentBroadCatch(call)) return null

        val implementations = ClassInheritorsSearch.search(interfaceClass, GlobalSearchScope.projectScope(interfaceClass.project), true)
            .findAll()
            .filter { !it.isInterface && !it.hasModifierProperty(PsiModifier.ABSTRACT) }
        if (implementations.size !in MIN_IMPLEMENTATIONS..MAX_IMPLEMENTATIONS) return null

        val thrownByImpl = implementations.associateWith { impl -> thrownExceptionNamesOf(impl, interfaceMethod) }
        val allThrown = thrownByImpl.values.flatten().toSet()

        for (exceptionName in allThrown) {
            val throwers = thrownByImpl.filterValues { exceptionName in it }.keys
            if (throwers.size < implementations.size) {
                return DivergenceHit(
                    anchorOf(call),
                    interfaceClass.name ?: "<interface>",
                    interfaceMethod.name,
                    exceptionName,
                    throwers.first().name ?: "<implementation>",
                )
            }
        }
        return null
    }

    /** The real override [impl] provides for [interfaceMethod] (or nothing if [impl] inherits it from elsewhere -- no NEW divergence introduced by this class itself), by the simple NAME of each directly-thrown exception. */
    private fun thrownExceptionNamesOf(impl: PsiClass, interfaceMethod: PsiMethod): Set<String> {
        val overriding = impl.findMethodBySignature(interfaceMethod, true) ?: return emptySet()
        if (overriding.containingClass != impl) return emptySet()
        val body = overriding.body ?: return emptySet()

        val result = mutableSetOf<String>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitLambdaExpression(expression: PsiLambdaExpression) { /* separate execution context -- never descended */ }
            override fun visitAnonymousClass(aClass: PsiAnonymousClass) { /* separate execution context -- never descended */ }
            override fun visitThrowStatement(statement: PsiThrowStatement) {
                super.visitThrowStatement(statement)
                val name = (statement.exception as? PsiNewExpression)?.classReference?.referenceName ?: return
                result += name
            }
        })
        return result
    }

    private fun isWrappedInSilentBroadCatch(call: PsiMethodCallExpression): Boolean {
        var tryStatement = PsiTreeUtil.getParentOfType(call, PsiTryStatement::class.java)
        while (tryStatement != null) {
            val tryBlock = tryStatement.tryBlock
            if (tryBlock != null && PsiTreeUtil.isAncestor(tryBlock, call, false)) {
                for (catchSection in tryStatement.catchSections) {
                    val catchTypeText = catchSection.parameter?.typeElement?.text ?: continue
                    val catchTypeNames = catchTypeText.split("|").map { it.trim().substringAfterLast('.') }
                    if (catchTypeNames.any { it in BROAD_CATCH_SIMPLE_NAMES } && isSilentlySwallowing(catchSection.catchBlock)) return true
                }
            }
            tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement::class.java)
        }
        return false
    }

    private fun isSilentlySwallowing(catchBlock: PsiCodeBlock?): Boolean {
        if (catchBlock == null) return false
        var swallows = true
        catchBlock.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitThrowStatement(statement: PsiThrowStatement) {
                swallows = false
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val name = expression.methodExpression.referenceName?.lowercase() ?: return
                if (name in LOGGING_METHOD_NAMES) swallows = false
            }
        })
        return swallows
    }

    private fun anchorOf(call: PsiMethodCallExpression): PsiElement =
        call.methodExpression.referenceNameElement ?: call.methodExpression
}
