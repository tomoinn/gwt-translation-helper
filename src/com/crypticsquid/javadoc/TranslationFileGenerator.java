package com.crypticsquid.javadoc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationValue;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;

/**
 * <p>
 * Doclet class to extract properties files from extensions of GWT's Constants
 * and Messages interfaces. Properties files will be written to
 * $SRC_PATH/translation/$LOCALE, with one file per relevant interface.
 * Properties are extracted and documented according to javadoc comments and the
 * values of the Meaning, Description and DefaultStringValue annotations. It
 * will pull in any values in properties files for the appropriate locale
 * already located in the source tree.
 * </p>
 * <p>
 * To run the doclet specify its class and path to the compiled class file to
 * javadoc along with local specified as -targetLocale LOCALE, i.e.
 * '-targetLocale de' for German
 * </p>
 * <p>
 * The doclet takes a mode parameter, if this is set to 'export' it creates the
 * translation files by scanning any existing translations along with the
 * metadata defined by the interfaces. If set to 'import' it reads these
 * translation files back in
 * </p>
 * This doclet is licensed under the MIT license, with many thanks to Green
 * Energy Options with whom I was working when it was written. More info on GEO
 * can be found online at http://http://www.greenenergyoptions.co.uk/
 * 
 * @author Tom Oinn (tomoinn@crypticsquid.com)
 */
public class TranslationFileGenerator {

	static List<TranslationTask> tasks = new ArrayList<TranslationTask>();

	static String NO_TRANSLATION = "TRANSLATE_ME";

	enum TranslationType {
		MESSAGES, CONSTANTS, NONE;
		static TranslationType forType(Type t) {
			if (t.qualifiedTypeName().equals("com.google.gwt.i18n.client.Constants")) {
				return CONSTANTS;
			} else if (t.qualifiedTypeName().equals("com.google.gwt.i18n.client.Messages")) {
				return MESSAGES;
			}
			return NONE;
		}
	}

	private static Map<Property, String> parseOptions(String options[][]) {
		Map<Property, String> result = new HashMap<Property, String>();
		for (int i = 0; i < options.length; i++) {
			String[] opt = options[i];
			Property prop = Property.fromPropName(opt[0]);
			if (prop != null) {
				result.put(prop, opt[1]);
			}
		}
		return result;
	}

	private static String getCommonRoot(List<String> paths) {
		String[][] names = new String[paths.size()][];
		for (int i = 0; i < paths.size(); i++) {
			names[i] = paths.get(i).split("\\.");
		}
		int common = 0;
		String rootString = "";
		boolean finished = false;
		while (!finished) {
			String check = null;
			for (String[] path : names) {
				if (path.length <= common) {
					finished = true;
				} else {
					if (check == null) {
						check = path[common];
					} else {
						if (path[common].equals(check) == false) {
							finished = true;
						}
					}
				}
			}
			if (!finished) {
				rootString += (common > 0 ? "." : "") + names[0][common];
				common++;
			}
		}
		return rootString;
	}

	public static boolean start(RootDoc root) throws IOException {
		Map<Property, String> props = parseOptions(root.options());
		String locale = props.get(Property.TARGET_LOCALE);
		String[] sourcePaths = props.get(Property.SOURCEPATH).split(";");
		for (ClassDoc classDoc : root.classes()) {
			Type[] interfaceTypes = classDoc.interfaceTypes();
			for (Type t : interfaceTypes) {
				switch (TranslationType.forType(t)) {
				case MESSAGES:
					tasks.add(new TranslationTask(classDoc, TranslationType.MESSAGES, locale, sourcePaths));
					break;
				case CONSTANTS:
					tasks.add(new TranslationTask(classDoc, TranslationType.CONSTANTS, locale, sourcePaths));
					break;
				default:
				}
			}
		}
		Collections.sort(tasks);

		List<String> paths = new ArrayList<String>();
		for (TranslationTask task : tasks) {
			paths.add(task.file);
		}
		String rootString = getCommonRoot(paths);

		String localeString = props.get(Property.TARGET_LOCALE);
		File sourceDir = new File(props.get(Property.SOURCEPATH).split(";")[0]);
		File translationDir = new File(new File(sourceDir.getParentFile(), "translation" + System.getProperty("file.separator") + localeString), rootString);

		if (props.get(Property.MODE).equalsIgnoreCase("export")) {
			/*
			 * Write out translation properties files
			 */
			translationDir.mkdirs();
			int constants = 0;
			int messages = 0;
			int variations = 0;
			for (TranslationTask task : tasks) {
				String contents = task.toString();
				String fileName = task.file.substring(rootString.equals("") ? 0 : rootString.length() + 1) + "_" + locale + ".properties";
				BufferedWriter writer = new BufferedWriter(new FileWriter(new File(translationDir, fileName)));
				writer.write(contents);
				writer.flush();
				writer.close();
				if (task.getTranslationType().equals(TranslationType.CONSTANTS)) {
					constants += task.getSize();
				} else {
					messages += task.getSize();
					variations += task.getVariations();
				}
			}
			System.out.println("Found " + constants + " constants to translate and " + messages + " messages with " + variations + " plural variants.");
			System.out.println("Written files to " + translationDir.getCanonicalPath());
		} else if (props.get(Property.MODE).equalsIgnoreCase("import")) {
			/*
			 * Scan directory for translations, pushing them back out into the
			 * appropriate locations in the source tree
			 */
			if (!translationDir.exists()) {
				System.out.println("Can't import translations, directory " + translationDir.getCanonicalPath() + " doesn't exist.");
			}
			for (File translationFile : translationDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File f) {
					return f.isFile();
				}
			})) {
				List<String> pathComponents = new ArrayList<String>();
				for (String item : rootString.split("\\.")) {
					pathComponents.add(item);
				}
				String[] nameParts = translationFile.getName().split("\\.");
				for (int i = 0; i < nameParts.length - 2; i++) {
					pathComponents.add(nameParts[i]);
				}
				File f = sourceDir;
				for (String item : pathComponents) {
					f = new File(f, item);
				}
				File outputFile = new File(f, nameParts[nameParts.length - 2] + ".properties");

				/*
				 * Read in the translated properties file and write out the target, this
				 * ensures we have a valid properties file (or will break)
				 */
				FileReader fr = new FileReader(translationFile);
				Properties p = new Properties();
				p.load(fr);
				fr.close();
				for (String propertyName : p.stringPropertyNames()) {
					if (p.getProperty(propertyName).equals(NO_TRANSLATION)) {
						p.remove(propertyName);
					}
				}
				FileWriter fw = new FileWriter(outputFile);
				p.store(fw, "");
				fw.flush();
				fw.close();
			}
			System.out.println("Imported translations back into source tree.");
		}

		return true;
	}

	static class TranslationTask implements Comparable<TranslationTask> {

		private final String file;
		private final String locale;
		private final TranslationType type;
		private final String[] sourcePaths;
		private final List<TranslateMe> elements = new ArrayList<TranslateMe>();

		TranslationTask(ClassDoc classDoc, TranslationType type, String targetLocale, String[] sourcePaths) {
			this.file = classDoc.qualifiedName();
			this.locale = targetLocale;
			this.sourcePaths = sourcePaths;
			this.type = type;
			Properties existingProperties = readExistingProperties();
			for (MethodDoc md : classDoc.methods()) {
				String propertyName = md.qualifiedName().substring(file.length() + 1);
				elements.add(new TranslateMe(md, type, existingProperties, propertyName));
			}
		}

		public Properties readExistingProperties() {
			Properties existingProperties = new Properties();
			try {
				for (String sourcePath : sourcePaths) {
					File f = new File(new File(sourcePath), file.replace('.', '/') + "_" + locale + ".properties");
					if (f.exists()) {
						FileReader fr = new FileReader(f);
						existingProperties.load(fr);
						fr.close();
					}
				}
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
			return existingProperties;
		}

		public int getSize() {
			return elements.size();
		}

		public int getVariations() {
			int count = 0;
			for (TranslateMe tm : elements) {
				count += tm.getVariations();
			}
			return count;
		}

		public TranslationType getTranslationType() {
			return type;
		}

		@Override
		public int compareTo(TranslationTask o) {
			if (type.equals(o.type)) {
				return file.compareTo(o.file);
			} else {
				return type.compareTo(o.type);
			}
		}

		@Override
		public String toString() {
			StringBuffer m = new StringBuffer();
			for (TranslateMe element : elements) {
				m.append("\n" + element);
			}
			return formatPropertyComment("This is a properties file defining constants or messages which require translation into the locale '" + locale
					+ "'. For each property please replace 'TRANSLATE_ME' with the appropriate translation. Where available current default values "
					+ "and any documentation are shown above each property to be translated.")
					+ m.toString();
		}

	}

	static class TranslateMe {

		final String defaultValue, descriptionAnnotation, meaningAnnotation, docString;
		final Properties props = new Properties();
		final List<String> vars = new ArrayList<String>();
		final Map<String, String> messageForms = new HashMap<String, String>();
		final List<String> formVars = new ArrayList<String>();

		TranslateMe(MethodDoc md, TranslationType type, Properties existingProperties, String propertyName) {
			defaultValue = (type.equals(TranslationType.CONSTANTS) ? (Annotation.DEFAULT_STRING_VALUE.existsIn(md) ? Annotation.DEFAULT_STRING_VALUE.valueIn(md).toString() : "")
					: (Annotation.DEFAULT_MESSAGE.existsIn(md) ? Annotation.DEFAULT_MESSAGE.valueIn(md).toString() : ""));
			docString = md.commentText().isEmpty() ? "Translation for '" + propertyName + "' (no doc comment supplied)" : md.commentText().replace("\n", "");
			descriptionAnnotation = Annotation.DESCRIPTION.existsIn(md) ? Annotation.DESCRIPTION.valueIn(md).toString() : null;
			meaningAnnotation = Annotation.MEANING.existsIn(md) ? Annotation.MEANING.valueIn(md).toString() : null;
			props.setProperty(propertyName, existingProperties.containsKey(propertyName) ? existingProperties.getProperty(propertyName) : NO_TRANSLATION);
			/* If we're parsing a messages interface we need to look for alternates */
			if (type.equals(TranslationType.MESSAGES) && Annotation.ALTERNATE_MESSAGE.existsIn(md)) {
				AnnotationValue[] forms = (AnnotationValue[]) Annotation.ALTERNATE_MESSAGE.valueIn(md).value();
				for (int i = 0; i < forms.length; i = i + 2) {
					messageForms.put("[" + forms[i].value().toString() + "]", forms[i + 1].value().toString());
					String expandedPropertyName = propertyName + "[" + forms[i].value() + "]";
					props.setProperty(expandedPropertyName, existingProperties.containsKey(expandedPropertyName) ? existingProperties.getProperty(expandedPropertyName) : "TRANSLATE_ME");
				}
			}
			/* We also need to add documentation for each numbered parameter */
			if (type.equals(TranslationType.MESSAGES)) {
				for (Parameter p : md.parameters()) {
					vars.add(p.toString());
					if (Annotation.PLURAL_COUNT.existsIn(p) || Annotation.SELECT.existsIn(p)) {
						formVars.add(p.name());
					}
				}
			}
		}

		int getVariations() {
			return messageForms.size();
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(formatPropertyComment(docString));
			if (descriptionAnnotation != null) {
				sb.append(formatPropertyMeta("@Description=", descriptionAnnotation));
			}
			if (descriptionAnnotation != null) {
				sb.append(formatPropertyMeta("@Meaning=", meaningAnnotation));
			}
			sb.append(formatPropertyMeta("@DefaultValue=", defaultValue));
			sb.append(formatIndexedList(vars));
			sb.append(formatMessageForms(messageForms, formVars));
			sb.append(BLANK + "\n");
			for (String propName : props.stringPropertyNames()) {
				sb.append(propName.replace("=", "\\=") + " = " + props.getProperty(propName) + "\n");
			}
			return sb.toString();
		}
	}

	private static final int LINE_LENGTH = 90;
	private static final String COMMENT_PREFIX = "#";
	private static final String BLANK = "# ----------------------------------------------------------------------------------------";

	static final String formatPropertyComment(String text) {
		return formatPropertyComment(text, " ", " ", false);
	}

	static final String padString(String s, int i) {
		while (s.length() < i) {
			s = s + " ";
		}
		return s;
	}

	static final String formatMessageForms(Map<String, String> map, List<String> formStrings) {
		if (map.isEmpty() || formStrings.isEmpty()) {
			return "";
		}
		int longestKey = 0;
		for (String key : map.keySet()) {
			if (longestKey < key.length()) {
				longestKey = key.length();
			}
		}
		StringBuffer sb = new StringBuffer();
		sb.append(BLANK + "\n");
		sb.append(formatPropertyComment("This message has alternate forms depending on the values of one or more of the input "
				+ "parameters. The variations and their corresponding defaults are shown below, the parameters " + "are specified in order and separated with the | character."));
		sb.append(COMMENT_PREFIX + "\n");
		StringBuffer joinedFormStrings = new StringBuffer();
		for (int i = 0; i < formStrings.size(); i++) {
			joinedFormStrings.append(i == 0 ? "[" + formStrings.get(i) : "|" + formStrings.get(i));
		}
		joinedFormStrings.append("]");
		sb.append(formatPropertyComment(joinedFormStrings.toString()));
		for (String key : map.keySet()) {
			sb.append(formatPropertyComment("if " + padString(key, longestKey) + " " + map.get(key), blankString(longestKey + 7), "   ", false));
		}
		return sb.toString();
	}

	static final String formatIndexedList(List<String> vars) {
		if (vars.isEmpty()) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		sb.append(BLANK + "\n");
		sb.append(formatPropertyComment("Message parameters", " ", " ", false));
		for (int i = 0; i < vars.size(); i++) {
			sb.append(formatPropertyComment("{" + i + "} " + vars.get(i), blankString(("{}" + i).length() + 2), "  ", false));
		}
		return sb.toString();
	}

	static final String formatPropertyMeta(String name, String value) {
		if (value == null) {
			return "";
		}
		String spacer = blankString(name.length() + 2);
		return formatPropertyComment(name + value, spacer, " ", true);
	}

	static final String formatPropertyComment(String text, String indent, String firstLineIndent, boolean newLineAtStart) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		String[] words = text.split(" ");
		String line = COMMENT_PREFIX + firstLineIndent;
		StringBuffer comment = new StringBuffer();
		boolean firstWordInLine = true;
		if (newLineAtStart) {
			comment.append(BLANK + "\n");
		}
		for (int i = 0; i < words.length; i++) {
			if (line.length() + words[i].length() > LINE_LENGTH) {
				comment.append(line + "\n");
				line = COMMENT_PREFIX + indent;
				firstWordInLine = true;
			}
			line = line + (firstWordInLine ? words[i] : " " + words[i]);
			firstWordInLine = false;
		}
		if (!firstWordInLine) {
			comment.append(line + "\n");
		}
		return comment.toString();
	}

	/**
	 * Create a blank string of the specified length
	 */
	static final String blankString(int length) {
		String value = "";
		for (int i = 0; i < length; i++) {
			value += " ";
		}
		return value;
	}

	/**
	 * Used during validation
	 */
	public static final int optionLength(String option) {
		return Property.fromPropName(option) != null ? 2 : 0;
	}

	/**
	 * Used during validation
	 */
	public static final boolean validOptions(String options[][], DocErrorReporter reporter) {
		Set<Property> definedProps = new HashSet<Property>();
		for (int i = 0; i < options.length; i++) {
			String[] opt = options[i];
			Property prop = Property.fromPropName(opt[0]);
			if (prop == null) {
				//
			} else if (definedProps.contains(prop)) {
				reporter.printError("Property '" + opt[0] + "' specified more than once!");
				return false;
			} else {
				definedProps.add(prop);
			}
		}
		if (definedProps.size() == Property.values().length) {
			/* Have all the properties, check that mode is either 'import' or 'export' */
			Map<Property, String> opts = parseOptions(options);
			String mode = opts.get(Property.MODE);
			if (mode.equals("import") || mode.equals("export")) {
				return true;
			} else {
				reporter.printError("Property 'mode' must have values [import|export]");
				return false;
			}
		} else {
			for (Property prop : Property.values()) {
				if (definedProps.contains(prop) == false) {
					reporter.printError("Property '" + prop.propName + "' not specified!");
				}
			}
			return false;
		}
	}

	/**
	 * Defines valid properties for this doclet
	 */
	static enum Property {
		TARGET_LOCALE("-targetLocale"), SOURCEPATH("-sourcepath"), MODE("-mode");

		private final String propName;

		Property(String propName) {
			this.propName = propName;
		}

		static final Property fromPropName(String targetPropName) {
			for (Property prop : values()) {
				if (prop.propName.equals(targetPropName)) {
					return prop;
				}
			}
			return null;
		}
	}

	/**
	 * Convenience enum to capture annotations we're interested in
	 */
	static enum Annotation {
		DEFAULT_STRING_VALUE("com.google.gwt.i18n.client.Constants.DefaultStringValue"),

		DEFAULT_MESSAGE("com.google.gwt.i18n.client.Messages.DefaultMessage"),

		DESCRIPTION("com.google.gwt.i18n.client.LocalizableResource.Description"),

		MEANING("com.google.gwt.i18n.client.LocalizableResource.Meaning"),

		ALTERNATE_MESSAGE("com.google.gwt.i18n.client.Messages.AlternateMessage"),

		PLURAL_COUNT("com.google.gwt.i18n.client.Messages.PluralCount"),

		SELECT("com.google.gwt.i18n.client.Messages.Select");

		final String className;

		Annotation(String className) {
			this.className = className;
		}

		AnnotationValue valueIn(ProgramElementDoc ped) {
			for (AnnotationDesc ad : ped.annotations()) {
				if (ad.annotationType().qualifiedName().equals(className)) {
					return ad.elementValues()[0].value();
				}
			}
			return null;
		}

		AnnotationDesc descIn(ProgramElementDoc ped) {
			for (AnnotationDesc ad : ped.annotations()) {
				if (ad.annotationType().qualifiedName().equals(className)) {
					return ad;
				}
			}
			return null;
		}

		boolean existsIn(ProgramElementDoc ped) {
			for (AnnotationDesc ad : ped.annotations()) {
				if (ad.annotationType().qualifiedName().equals(className)) {
					return true;
				}
			}
			return false;
		}

		boolean existsIn(Parameter ped) {
			for (AnnotationDesc ad : ped.annotations()) {
				if (ad.annotationType().qualifiedName().equals(className)) {
					return true;
				}
			}
			return false;
		}
	}

}
