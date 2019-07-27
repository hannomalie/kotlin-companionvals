package de.hanno.companionvals

import com.google.auto.service.AutoService
import de.hanno.companionvals.CompanionValsAnnotationProcessor.Companion.KAPT_KOTLIN_GENERATED_OPTION_NAME
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.jvm.internalName
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import java.io.File
import java.lang.IllegalStateException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic

private val String.excludedFromCompanion: Boolean
    get() {
        return listOf("component",
           "copy",
           "equals",
           "hashCode",
           "toString").any {

            startsWith(it) // TODO: Use startsWith only for component functions
        }
    }

typealias ProtoFun = ProtoBuf.Function

@AutoService(Processor::class)
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_6)
class CompanionValsAnnotationProcessor: AbstractProcessor() {
    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(de.hanno.companionvals.Companion::class.java.name)
    }
    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(de.hanno.companionvals.Companion::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Can't find the target directory for generated Kotlin files.")
            return false
        }

        val annotationsToClass = annotatedElements
            .groupBy { it.enclosingElement }

        for(enclosingElementAndElement in annotationsToClass) {
            val enclosingElement = enclosingElementAndElement.key as? TypeElement ?: return false
            val classMembers = processingEnv.elementUtils.getAllMembers(enclosingElement)

            val metadata = enclosingElement.kotlinMetadata as? KotlinClassMetadata ?: return false
            val classData = metadata.data

            val (nameResolver, classProto) = classData

            class Parameter(val name: String)

            val properties = classProto.propertyList.map { property ->
                Parameter(nameResolver.getString(property.name))
            }

            val resultingSourceCode = properties.map { constructorParameter ->
                val classMember = classMembers.first { it.simpleName.toString() == constructorParameter.name }
                val classMemberTypeElement = (classMember.asType() as DeclaredType).asElement() as TypeElement

                val isProtectedOrPrivate = classMemberTypeElement.modifiers.any { it == Modifier.PRIVATE || it == Modifier.PROTECTED }
                if(isProtectedOrPrivate) throw IllegalStateException("Private or protected properties are not allowed as companions!\n" +
                        "Found property ${constructorParameter.name} with visibility ${classMember.modifiers.joinToString(",")}")

                val propertyClassData = (classMemberTypeElement.kotlinMetadata as KotlinClassMetadata).data
                val propertyClassProto = propertyClassData.proto
                val propertyCompanions = propertyClassProto.propertyList.map { property ->
                    val propertyName = propertyClassData.nameResolver.getQualifiedClassName(property.name)
                    val receiver = enclosingElement.qualifiedName
                    "val $receiver.$propertyName \n\tget() = this.${classMember.internalName}.$propertyName"
                }.fold("") { a, b -> a + "\n" + b}

                fun ProtoFun.isExcludedFromCompanion() = propertyClassData.nameResolver.getQualifiedClassName(name).excludedFromCompanion

                val functionCompanions = propertyClassProto.functionList.filterNot { it.isExcludedFromCompanion() }.map { function ->
                    val functionName = propertyClassData.nameResolver.getQualifiedClassName(function.name)
                    val hasDefaultsForAllParams = function.valueParameterList.all { it.declaresDefaultValue }
                    val hasAnyDefaultParams = function.valueParameterList.any { it.declaresDefaultValue }
                    if(!hasDefaultsForAllParams && hasAnyDefaultParams) {
                        throw IllegalStateException("Cannot make companion of function $functionName of class ${enclosingElementAndElement.key}. Please exclude it via annotation parameters.") // TODO: Make this possible
                    }
                    val receiver = enclosingElement.qualifiedName
                    val parameterLessVersionOrEmpty = if(hasDefaultsForAllParams) "fun $receiver.$functionName() = this.${classMember.internalName}.$functionName()\n" else ""
                    val parameterString = function.valueParameterList.map { parameter ->
                        "${propertyClassData.nameResolver.getString(parameter.name)}: ${parameter.type.extractFullName(propertyClassData)}"
                    }.reduce { a, b -> "$a, $b" }
                    val argumentsString = function.valueParameterList.map { parameter ->
                        propertyClassData.nameResolver.getString(parameter.name)
                    }.reduce { a, b -> "$a, $b" }
                    parameterLessVersionOrEmpty + "fun $receiver.$functionName($parameterString) = this.${classMember.internalName}.$functionName($argumentsString)"
                }.fold("") { a, b -> a + "\n" + b}

                "$propertyCompanions\n$functionCompanions"
            }.fold("") { a, b -> a + "\n" + b}

            File(kaptKotlinGeneratedDir, "${enclosingElement.simpleName}CompanionVals.kt").apply {
                parentFile.mkdirs()
                writeText(resultingSourceCode)
            }
        }

        return true
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}