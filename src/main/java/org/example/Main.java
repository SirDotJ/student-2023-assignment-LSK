package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.*;

public class Main {
	private static final String REPLACEMENT_JSON = "replacement.json";
	private static final String RESULT_JSON = "result.json";
	private static final String REPLACEMENT_KEY_STRING = "replacement";
	private static final String SOURCE_KEY_STRING = "source";
	public static void main(String[] args) throws Exception {
		String urlForApi = "";
		if (args.length == 0) {
			System.out.print("Enter API URL: ");
			Scanner input = new Scanner(System.in);
			urlForApi = input.next();
		} else {
			urlForApi = args[0];
		}
		printResultToJson(fixMessages(urlForApi));
		System.out.println("Fix finished! Look in the folder for \"" + RESULT_JSON + "\" file");
	}
	private static String readFile(String path) throws IOException, URISyntaxException {
		String fileName = Paths.get(path).toString();

		FileInputStream fileInputStream = new FileInputStream(fileName);
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));

		String content = "";
		String line = "";
		while ((line = bufferedReader.readLine()) != null)
			content += line + "\n";

		bufferedReader.close();
		fileInputStream.close();

		return content;
	}
	private static String getRaw(String urlToRead) throws Exception {
		StringBuilder result = new StringBuilder();

		URL url = new URL(urlToRead);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(conn.getInputStream())
		)) {
			for (String line; (line = reader.readLine()) != null;) {
				result.append(line);
			}
		}
		return result.toString();
	}
	private static Vector<String> separateStrings(String raw) {
		Vector<String> output = new Vector<>();
		Pattern pattern = Pattern.compile("\"(.*?)\"");
		Matcher matcher = pattern.matcher(raw);
		while (matcher.find()) {
			output.add(matcher.group(1));
		}
		return output;
	}
	private static JSONArray buildJson(String input) {
		return new JSONArray(input);
	}
	private static JSONObject readObject(String input) {
		return new JSONObject(input);
	}

	/**
	 * Result will be a set of replacement/source pairs
	 * if there are multiple sources for the same replacement,
	 * then only the last one will be considered
	 */
	private static Vector<String[]> parseRules(String inputFileUrl) throws IOException, URISyntaxException {
		Vector<String[]> output = new Vector<>();

		String rawRules = readFile(inputFileUrl);
		JSONArray array = buildJson(rawRules);
		for (var data :
				array) {
			addRule(output, new JSONObject(data.toString()));
		}

		return output;
	}
	private static void addRule(Vector<String[]> rules, JSONObject rule) {
		String[] newRule = new String[2];
		newRule[0] = rule.get(REPLACEMENT_KEY_STRING).toString();
		newRule[1] = rule.get(SOURCE_KEY_STRING).toString();
		for (var oldRule :
				rules) {
			if (oldRule[0].compareTo(newRule[0]) == 0) {
				rules.remove(oldRule);
				break;
			}
		}
		rules.add(newRule);
	}
	private static Vector<String> fixMessages(String urlToRead) throws Exception {
		String raw = getRaw(urlToRead);
		Vector<String> brokenMessages = separateStrings(raw);

		Vector<String> fixedMessages = new Vector<>();
		Vector<String[]> replacementRules = sortRules(parseRules(REPLACEMENT_JSON));

		for (String currentMessage :
				brokenMessages) {
			String fixedMessage = String.copyValueOf(currentMessage.toCharArray());
			for (var rule :
					replacementRules) {
				if(currentMessage.contains(rule[0])) {
					if (rule[1] == null) {
						fixedMessage = currentMessage.replaceAll(rule[0], "");
					} else {
						fixedMessage = currentMessage.replaceAll(rule[0], rule[1]);
					}
				}
				currentMessage = String.copyValueOf(fixedMessage.toCharArray());
			}
			if(fixedMessage.compareTo("null") != 0)
				fixedMessages.add(fixedMessage);
		}
		return fixedMessages;
	}
	private static void printResultToJson(Vector<String> fixedMessages) throws IOException {
		JSONArray jsonArray = new JSONArray(fixedMessages);
		FileWriter fileWriter = new FileWriter(RESULT_JSON);
		jsonArray.write(fileWriter);
		fileWriter.close();
	}

	/**
	 * Sorts rules by declining of their "complexity" (how many symbols it replaces at once)
	 * this is done to ensure that smaller rules do not get priority over bigger rules so that in case of
	 * one rule being inside another the complex one will be done first
	 */
	private static Vector<String[]> sortRules(Vector<String[]> unsortedRules) {
		Map<Integer, Integer> lengthIndexMap = new HashMap<>();
		int i = 0;
		for (String[] pair :
				unsortedRules) {
			lengthIndexMap.put(i++, pair[0].length());
		}
		List<Map.Entry<Integer, Integer>> sortedRulesList = new ArrayList<>(lengthIndexMap.entrySet());
		sortedRulesList.sort(Map.Entry.comparingByValue());

		Vector<String[]> output = new Vector<>();
		Collections.reverse(sortedRulesList);
		sortedRulesList.forEach((entry) -> {
			output.add(unsortedRules.get(entry.getKey()));
		});
		return output;
	}
}

