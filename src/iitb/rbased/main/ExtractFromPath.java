package iitb.rbased.main;

import iitb.rbased.meta.KeywordData;
import iitb.rbased.util.Country;
import iitb.rbased.util.Number;
import iitb.rbased.util.Pair;
import iitb.rbased.util.Relation;
import iitb.rbased.util.Word;
import iitb.rbased.util.graph.Graph;

import java.util.ArrayList;
import java.util.HashSet;

//sg

//The path should have one of the keywords, and should not have any 
//modifying words
public class ExtractFromPath {

	public static final Integer AGL = 0;
	public static final Integer FDI = 1;
	public static final Integer GOODS = 2;
	public static final Integer ELEC = 3;
	public static final Integer CO2 = 4;
	public static final Integer INF = 5;
	public static final Integer INTERNET = 6;
	public static final Integer GDP = 7;
	public static final Integer LIFE = 8;
	public static final Integer POP = 8;
	public static final Integer DIESEL = 9;

	/*
	 * 
	 * public static final Integer NUM_RELATIONS = 11; static String relName[] =
	 * {"AGL", "FDI", "GOODS", "ELEC", "CO2", "INF", "INTERNET", "GDP", "LIFE",
	 * "POP", "DIESEL"}; static String KEYWORDS[][] = { {"area", "land",
	 * "land area"}, {"foreign", "FDI", "direct", "investments"}, {"goods"},
	 * {"Electricity", "kilowatthors", "Terawatt"}, {"Carbon",
	 * "Carbon Emission", "CO2"}, {"Inflation", "Price Rise"}, {"Internet",
	 * "users"}, {"Gross domestic", "GDP"}, {"life", "life expectancy"},
	 * {"population", "people", "inhabitants", "natives"}, {"diesel"}, }; static
	 * String modifiers[] = {"change", "up", "down", "males", "females", "male",
	 * "female", "growth", "increase", "decrease", "decreased", "increased",
	 * "changed"};
	 */

	/**
	 * The following 2 functions simply test for existence of a keyword on the
	 * specified path The function is called once for each of the relations
	 * (i.e. with the keyword set of each of the relation and checks if one of
	 * the words on the path is the keyword
	 * 
	 * @param path
	 * @param keywords
	 * @param depGraph
	 * @return Keyword on the path if one is found
	 */
	static Word getKeyword(ArrayList<Word> path, ArrayList<String> keywords, Graph depGraph) {
		for (String kw : keywords) { // for each of the keywords
			kw = kw.toLowerCase();
			for (Word wordOnPath : path) { // iterate over the word and see if
											// there is a match
				if (wordOnPath.getVal().toLowerCase().equals(kw)) {
					return wordOnPath;
				} else {
					HashSet<Word> modWords = depGraph.getKeywordModifiers(wordOnPath);
					if (modWords == null)
						continue; // no modifier words.
					for (Word modWord : modWords) {
						if (modWord.getVal().toLowerCase().equals(kw)) {
							return modWord;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Returns all the relations that can exist between the argPair, path is the
	 * list of words that appear in the dependency graph between the argPair
	 * 
	 * @param argPair
	 * @param path
	 * @param depGraph
	 * @return
	 */
	public static ArrayList<Relation> getExtractions(Pair<Country, Number> argPair, ArrayList<Word> path, Graph depGraph) {


		ArrayList<Relation> res = new ArrayList<Relation>();

		

		// checking if the label before the number is to. Checking one more
		// label to accompany units if any.

		
		/**
		 * Check if the number is a modified quantity and not the actual
		 * quantity. For example,
		 * "Increase in population as opposed to decrease in population"
		 */
		Integer numNode = depGraph.getIdx(argPair.second.getVal());
		String prevLabel = depGraph.getLabel(numNode - 1);
		String prev_prevLabel = depGraph.getLabel(numNode - 2);
		boolean prepositionPresent = prevLabel != null && prevLabel.equals("to") || prev_prevLabel != null
				&& prev_prevLabel.equals("to"); // increased to
		// ignore modifier for this number
		if (!prepositionPresent && modifierPresent(argPair, path)) {
			return res;
		}

		Word keyword = null;
		for (int i = 0; i < KeywordData.NUM_RELATIONS; i++) {

			if (null != (keyword = getKeyword(path, KeywordData.KEYWORDS.get(i), depGraph))) {
				assert (keyword != null);
				// System.out.println("Keyword ==> " + keyword);
				res.add(new Relation(argPair.first, argPair.second, keyword, KeywordData.relName.get(i)));
			}
		}
		return res;
	}

	/**
	 * check if the dependency path involves a modification.
	 * 
	 * @return
	 */
	public static boolean isModified(Graph depGraph, Pair<Country, Number> argPair,
			ArrayList<Word> path) {
		Integer numNode = depGraph.getIdx(argPair.second.getVal());
		String prevLabel = depGraph.getLabel(numNode - 1);
		String prev_prevLabel = depGraph.getLabel(numNode - 2);
		boolean prepositionPresent = prevLabel != null && prevLabel.equals("to") || prev_prevLabel != null
				&& prev_prevLabel.equals("to"); // increased to
		// ignore modifier for this number
		if (!prepositionPresent && modifierPresent(argPair, path)) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if a modifier is present in the dependency path
	 * @param argPair
	 * @param path
	 * @param kwd
	 * @return
	 */
	public static boolean modifierPresent(Pair<Country, Number> argPair, ArrayList<Word> path) {
		boolean modifierPresent = false;
		// if modifiers are present, cannot be extraction
		for (String mod : KeywordData.modifiers) {
			modifierPresent = modifierPresent || Word.wordListContainsVal(path, mod);
			if (modifierPresent) {
				return true; // return empty result
			}
		}
		return false;
	}
}
