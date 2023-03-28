-optimizationpasses 5

-dontwarn lombok.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn com.sun.management.OperatingSystemMXBean

#-forceprocessing
#-addconfigurationdebugging

-keep enum * { *; }

-keep class com.milaboratory.mitool.cli.** { *; }
-keep class com.milaboratory.mitool.refinement.gfilter.** { *; }

-keep class com.milaboratory.milm.MiXCRMain { public static void main(); }

-keep class com.milaboratory.mixcr.cli.** { *; }
-keep class com.milaboratory.mixcr.presets.** { *; }
-keep class com.milaboratory.mixcr.postanalysis.ui.** { *; }
-keep class com.milaboratory.milm.metric.** { *; }
-keep class com.milaboratory.**.*Parameters* { *; }
-keep class com.milaboratory.**.*Parameters*$* { *; }
-keep class com.milaboratory.**.*Report { *; }
-keep class com.milaboratory.**.*Report$* { *; }

-keep class io.repseq.gen.dist.*Model { *; }
-keep class io.repseq.gen.dist.*Model$* { *; }

-keep @com.milaboratory.util.DoNotObfuscate class *
-keep @com.milaboratory.util.DoNotObfuscateFull class * { *; }

#seiralization of primitivio
-keep class * extends com.milaboratory.primitivio.Serializer
-keep class * extends com.milaboratory.primitivio.ObjectMapperProvider

#usage of jackson
-keep class * extends com.fasterxml.jackson.**
-keep @com.fasterxml.jackson.** class * { *; }
# for some reason it's not working
#-keep class * extends @com.fasterxml.jackson.** * { *; }

#for usage of kotlinx.dataframe
-keep @org.jetbrains.kotlinx.dataframe.annotations.DataSchema class * { *; }


#for TypeReference of jackson
-keepattributes Signature
#for jackson serialization of inner classes
-keepattributes InnerClasses
#for working reflection
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

#for readable stacktrace
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable
-keepattributes SourceFile

#-keepattributes SourceDir
#-keepattributes Record
#-keepattributes PermittedSubclasses
#-keepattributes EnclosingMethod
#-keepattributes Deprecated
#-keepattributes Synthetic
#-keepattributes MethodParameters
#-keepattributes Exceptions
#-keepattributes LocalVariableTable
#-keepattributes LocalVariableTypeTable
#-keepattributes RuntimeInvisibleAnnotations
#-keepattributes RuntimeInvisibleParameterAnnotations
#-keepattributes RuntimeVisibleTypeAnnotations
#-keepattributes RuntimeInvisibleTypeAnnotations

#for working reflection
-keep class kotlin.Metadata

-repackageclasses com.milaboratory.o
-allowaccessmodification

-verbose
-dontnote com.milaboratory.primitivio.SerializersManager
-dontnote org.apache.**
-dontnote jetbrains.**
