package de.hanno.companionvals

import com.google.auto.service.AutoService
import de.hanno.companionvals.CompanionValsAnnotationProcessor.Companion.KAPT_KOTLIN_GENERATED_OPTION_NAME
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.jvm.internalName
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.type
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ElementVisitor
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeVisitor
import javax.lang.model.util.TypeKindVisitor6
import javax.tools.Diagnostic
import javax.tools.JavaFileObject

private val String.excludedFromCompanion: Boolean
    get() {
        return listOf("component",
           "copy",
           "equals",
           "hashCode",
           "toString").any {

            startsWith(it)
        }
    }

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

            fun ProtoBuf.Type.extractFullName() = extractFullName(classData)

            class Parameter(val name: String, val fqClassName: String)

            val constructorParameters = classProto.constructorList
                .single { it.isPrimary }
                .valueParameterList
                .map { valueParameter ->
                    Parameter(
                        name = nameResolver.getString(valueParameter.name),
                        fqClassName = valueParameter.type.extractFullName()).apply {
                    }
                }

            val classMember = classmembers.first { it.simpleName.toString() == constructorParameters[0].name }
            val classMemberTypeElement = (classMember.asType() as DeclaredType).asElement() as TypeElement
            val propertyClassData = (classMemberTypeElement.kotlinMetadata as KotlinClassMetadata).data
            val propertyClassProto = propertyClassData.proto
            val propertyCompanions = propertyClassProto.propertyList.map { property ->
                val memberClassName = propertyClassData.nameResolver.getQualifiedClassName(property.name)
                val receiver = enclosingElement.qualifiedName
                "val $receiver.$memberClassName \n\tget() = this.${classMember.internalName}.$memberClassName"
            }.reduce { a, b -> a + "\n" + b}

            val functionCompanions = propertyClassProto.functionList.map { function ->
                val memberClassName = propertyClassData.nameResolver.getQualifiedClassName(function.name)
                if(memberClassName.excludedFromCompanion) {
                    null
                } else {
                    val receiver = enclosingElement.qualifiedName
                    "fun $receiver.$memberClassName \n\t() = this.${classMember.internalName}.$memberClassName()"
                }
            }.filterNotNull().reduce { a, b -> a + "\n" + b}

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