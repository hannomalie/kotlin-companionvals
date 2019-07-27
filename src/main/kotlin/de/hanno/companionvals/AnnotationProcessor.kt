package de.hanno.companionvals

import com.google.auto.service.AutoService
import de.hanno.companionvals.CompanionValsAnnotationProcessor.Companion.KAPT_KOTLIN_GENERATED_OPTION_NAME
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.jvm.internalName
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.type
import java.io.File
import java.lang.IllegalStateException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
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
            val classmembers = processingEnv.elementUtils.getAllMembers(enclosingElement)

            val metadata = enclosingElement.kotlinMetadata as? KotlinClassMetadata ?: return false
            val classData = metadata.data

            val (nameResolver, classProto) = classData

            class Parameter(val name: String, val fqClassName: String)

            val constructorParameters = classProto.constructorList
                .single { it.isPrimary }
                .valueParameterList
                .map { valueParameter ->
                    Parameter(
                        name = nameResolver.getString(valueParameter.name),
                        fqClassName = valueParameter.type.extractFullName(classData)).apply {
                    }
                }

            val classMember = classmembers.first { it.simpleName.toString() == constructorParameters[0].name } // TODO: Iterate all params
            val classMemberTypeElement = (classMember.asType() as DeclaredType).asElement() as TypeElement
            val propertyClassData = (classMemberTypeElement.kotlinMetadata as KotlinClassMetadata).data
            val propertyClassProto = propertyClassData.proto
            val propertyCompanions = propertyClassProto.propertyList.map { property ->
                val propertyName = propertyClassData.nameResolver.getQualifiedClassName(property.name)
                val receiver = enclosingElement.qualifiedName
                "val $receiver.$propertyName \n\tget() = this.${classMember.internalName}.$propertyName"
            }.reduce { a, b -> a + "\n" + b}

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
                parameterLessVersionOrEmpty + "fun $receiver.$functionName($parameterString) = this.${classMember.internalName}.$functionName()"
            }.reduce { a, b -> a + "\n" + b}

            File(kaptKotlinGeneratedDir, "${enclosingElement.simpleName}CompanionVals.kt").apply {
                parentFile.mkdirs()
                writeText("$propertyCompanions\n$functionCompanions")
            }

        }

        return true
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}