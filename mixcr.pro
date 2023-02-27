#-addconfigurationdebugging

#-keep class com.milaboratory.**, io.repseq.** { *; }
-keep class com.milaboratory.mixcr.cli.** { *; }
-keep class **.*Version* { *; }
-keep class com.milaboratory.milm.MiXCRMain { *; }
#TODO it should be obfuscated
#-keep class com.milaboratory.milm.LM { *; }

-keep @com.fasterxml.jackson.annotation.* class **.* { *; }

#-keepclassmembers class * {
#  @com.fasterxml.jackson.annotation.JsonProperty *;
#}
#-keepclassmembers class * {
#  @com.fasterxml.jackson.annotation.JsonCreator *;
#}


#for TypeReference of jackson
-keepattributes Signature
#for jackson serialization of inner classes
-keepattributes InnerClasses
#for working reflection
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

#-keepattributes SourceFile
#-keepattributes SourceDir
#-keepattributes Record
#-keepattributes PermittedSubclasses
#-keepattributes EnclosingMethod
#-keepattributes Deprecated
#-keepattributes Synthetic
#-keepattributes MethodParameters
#-keepattributes Exceptions
#-keepattributes LineNumberTable
#-keepattributes LocalVariableTable
#-keepattributes LocalVariableTypeTable
#-keepattributes RuntimeInvisibleAnnotations
#-keepattributes RuntimeInvisibleParameterAnnotations
#-keepattributes RuntimeVisibleTypeAnnotations
#-keepattributes RuntimeInvisibleTypeAnnotations

#TODO try remove (will be saved by -keep of target class)
#for working reflection
-keep class kotlin.Metadata

#-flattenpackagehierarchy

-verbose
-dontnote com.milaboratory.primitivio.SerializersManager
-dontnote org.apache.**
-dontnote jetbrains.**
