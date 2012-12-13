# Translation file generating doclet
Google Web Toolkit (GWT) provides internationalisation support through two interfaces, Constants and Messages, which are used for constant strings and parameterised strings respectively. More information can be found at https://developers.google.com/web-toolkit/doc/latest/DevGuideI18n
Values for different locales are specified by properties files, i.e. MyConstantsInterface_de.properties for a German version of the strings in the MyConstantsInterface. These properties files are placed in the source tree along with their corresponding interfaces, which works well enough when you have one or two but is a nightmare for a large heavily modular project where an external agency is going to be doing the translations!
This doclet helps manage these properties files. It operates in two distinct modes, import and export, and is passed the target locale (i.e. 'de' for German).

## Export
In this mode the doclet will search the source tree for interfaces extending either the Constants or Messages interface, parse them, extract as much metadata as possible from both annotations and javadoc on those interfaces and build a properties file per interface. Properties files are written out to a 'translation/$LOCALE' folder parallel to the first item on the source path supplied to the doclet. Within this folder will be a single sub-folder named as the common package name for all interfaces and within this will be a single properties file per interface. These are the files you need to translate.

## Import
In this mode any translation files will be parsed and translated properties written back out to properties files in the appropriate location in the source tree ready to be picked up by GWT's compiler.

## Building
As the doclet APIs aren't part of the standard Java language you need to add the tools.jar (found in jdk/lib) to your classpath when building.

## Usage
Once compiled the doclet can be run from the command line or other interface of your choice, for example
```
javadoc -doclet com.crypticsquid.javadoc.TranslationFileGenerator -docletpath $PATH_TO_COMPILED_DOCLET -targetLocale $LOCALE -mode [import|export] ...(other standard javadoc options)

```

## Credits
Written by Tom Oinn (tomoinn@crypticsquid.com) while contracting at Green Energy Options (http://greenenergyoptions.co.uk), many thanks to GEO for agreeing to open source this code.