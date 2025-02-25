package iitb.rbased.main;

import iitb.rbased.meta.KeywordData;
import iitb.rbased.meta.RelationMetadata;
import iitb.rbased.util.Country;
import iitb.rbased.util.Number;
import iitb.rbased.util.Pair;
import iitb.rbased.util.Relation;
import iitb.rbased.util.Word;
import iitb.rbased.util.graph.Graph;
import iitb.shared.EntryWithScore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import catalog.QuantityCatalog;
import catalog.Unit;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Triple;
import eval.UnitExtractor;

//sg
public class RuleBasedDriver {
	private Properties prop;
	private StanfordCoreNLP pipeline;
	private static Pattern numberPat, yearPat;
	private HashSet<String> countryList;
	
	private boolean unitsActive;
	private static final String countriesFileName = "/mnt/a99/d0/ashishm/workspace/depbased/data/countries_list";
	private UnitExtractor ue = null;
	int cumulativeLen; // to obtain sentence offsets

	public RuleBasedDriver(boolean unitsActive) {
		this.unitsActive = unitsActive;
		numberPat = Pattern.compile("^[\\+-]?\\d+([,\\.]\\d+)*([eE]-?\\d+)?$");
		yearPat = Pattern.compile("^19[56789]\\d|20[01]\\d$");
		prop = new Properties();
		prop.put("annotators", "tokenize, ssplit, pos, lemma , parse");
		pipeline = new StanfordCoreNLP(prop);

		// Read the countries file

		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(countriesFileName));
			String countryName = null;
			countryList = new HashSet<>();
			while ((countryName = br.readLine()) != null) {
				countryList.add(countryName.toLowerCase());
			}
			br.close();
		} catch (IOException e) {
			System.err.println(e);
		}

		try {
			ue = new UnitExtractor();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*
		 * quantDict = new QuantityCatalog((Element) null); if(quantDict ==
		 * null){ System.err.println("Could not load Quantity Taxonomy file.");
		 * throw new Exception("Failed to load Quantity Taxonomy file."); }
		 */
	}

	public RuleBasedDriver(UnitExtractor ue, boolean unitsActive) {

		this(unitsActive);
		this.ue = ue;

	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		RuleBasedDriver rbased = new RuleBasedDriver(true);
		String inputFile = "/mnt/a99/d0/aman/test.in";
		String fileString = FileUtils.readFileToString(new File(inputFile));
		String outFile = "/mnt/a99/d0/aman/test.out";
		BufferedReader br = new BufferedReader(new FileReader(inputFile));
		String sent = null;

		PrintWriter pw = new PrintWriter(outFile);

		while ((sent = br.readLine()) != null) {
			try {
				pw.write(sent + "\n");
				ArrayList<Relation> results = rbased.extract(sent);
				for (Relation r : results) {
					pw.write(r + "\n");
					pw.flush();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		pw.close();
		br.close();
		// rbased.batchExtract(fileString, outFile);
		// System.out.println("here");
		// String fileString = FileUtils.readFileToString(new File("debug"));
		// System.out.println(rbased.extract(fileString));
	}

	public Graph getDepGraph(List<Triple<Integer, String, Integer>> deps,
			CoreMap sentence) {
		List<CoreLabel> tokens = sentence
				.get(CoreAnnotations.TokensAnnotation.class);
		int numTokens = tokens.size();
		Word wordArr[] = new Word[numTokens + 1];
		wordArr[0] = new Word(0, "ROOT", 0, 0);

		for (int i = 1; i <= numTokens; i++) {

			CoreLabel token = tokens.get(i - 1);

			String tokenStr = tokens.get(i - 1).toString();
			int begOffset = token
					.get(CoreAnnotations.TokenBeginAnnotation.class);
			int endOffset = token.get(CoreAnnotations.TokenEndAnnotation.class);
			wordArr[i] = new Word(i, tokenStr, begOffset, endOffset);

		}

		ArrayList<Pair<String, Pair<Word, Word>>> pairList = new ArrayList<Pair<String, Pair<Word, Word>>>();
		for (Triple<Integer, String, Integer> dep : deps) {
			// dep is given in the form indexOne Relation indexTwo
			int govIdx = dep.first;
			String rel = dep.second;
			int depIdx = dep.third;
			// Word govWord = new Word(govIdx, tokens.get(govIdx).toString());
			// Word depWord = new Word(depIdx, tokens.get(depIdx).toString());
			Word govWord = wordArr[govIdx];
			Word depWord = wordArr[depIdx];
			pairList.add(new Pair<String, Pair<Word, Word>>(rel,
					new Pair<Word, Word>(govWord, depWord)));
		}
		Graph depGraph = Graph.makeDepGraphFromList(pairList, wordArr);
		return depGraph;
	}

	/**
	 * takes a tokenized sentence, and the corresponding typed dependencies.
	 * Primarily written to facilitate talking with MultiR
	 * 
	 * @throws IOException
	 */
	public ArrayList<Relation> extractFromMultiRDepString(
			List<Triple<Integer, String, Integer>> deps, CoreMap sentence)
			throws IOException {
		ArrayList<Relation> res = new ArrayList<Relation>();

		Graph depGraph = getDepGraph(deps, sentence);

		// Step 3 : Identify all the country number word pairs
		ArrayList<Pair<Country, Number>> pairs = getPairs(depGraph, sentence);

		// Step 4 : Extract the relations that exists in these pairs
		res.addAll(getExtractions(depGraph, pairs, false));

		return res;
	}

	/**
	 * This is a weak version of the extract relation version and is written
	 * specifically to be used with the numbertron. The idea is to create the
	 * graph, spot the country and the number, and then check for: a) Absence of
	 * delta words b) Unit coherence
	 * 
	 * @param deps
	 * @param sentence
	 * @return
	 * @throws IOException
	 */
	public ArrayList<Relation> spotPossibleRelations(
			List<Triple<Integer, String, Integer>> deps, CoreMap sentence, boolean includeNAs, double NAFracs)
			throws IOException {
		ArrayList<Relation> res = new ArrayList<Relation>();

		Graph depGraph = getDepGraph(deps, sentence);

		// Step 3 : Identify all the country number word pairs
		ArrayList<Pair<Country, Number>> pairs = getPairs(depGraph, sentence);

		// Step 4 : Extract the relations that exists in these pairs
		res.addAll(getSpots(depGraph, pairs, includeNAs, NAFracs));

		return res;

	}

	/**
	 * Returns a list of relations spotted by using the Numberule
	 * @param depGraph
	 * @param pairs
	 * @param includeNA whether to include the No attachment class or not
	 * @param NAFraction the fraction of spots that are no attachments in the instances file
	 * @return The list of relations
	 * @throws IOException
	 */
	ArrayList<Relation> getSpots(Graph depGraph,
			ArrayList<Pair<Country, Number>> pairs, boolean includeNA, double NAFraction) throws IOException {

		ArrayList<Relation> result = new ArrayList<Relation>();
		for (Pair<Country, Number> pair : pairs) {
			ArrayList<Word> wordsOnDependencyGraphPath = depGraph
					.getWordsOnPath(pair.first, pair.second);
			boolean modified = ExtractFromPath.isModified(depGraph, pair,
					wordsOnDependencyGraphPath);
			if (modified) {
				
				continue;
			}
			if(includeNA && (Math.random() <= NAFraction)) {
				result.add(new Relation(pair.first, pair.second, null, "NA"));
			}
			/**
			 * every relation for which the units are compatible is a
			 * possibility, add them all
			 */
			for (int i = 0; i < KeywordData.NUM_RELATIONS; i++) {
				if (unitRelationMatch(KeywordData.relName.get(i), pair)) {
					result.add(new Relation(pair.first, pair.second, null,
							KeywordData.relName.get(i)));
				}
			}

		}

		return result;
	}

	public Graph constructDepGraph(String sentenceString) {
		Annotation doc = new Annotation(sentenceString);
		pipeline.annotate(doc);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
		List<CoreMap> sentences = doc.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Get dependency graph

			// Step 1 : Get the typed dependencies
			Tree tree = sentence.get(TreeAnnotation.class);

			GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
			GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);

			// Collection<TypedDependency> td = gs.typedDependenciesCollapsed();
			Collection<TypedDependency> td = gs.allTypedDependencies();
			// Collection<TypedDependency> td =
			// gs.typedDependenciesCCprocessed();
			Iterator<TypedDependency> tdi = td.iterator();

			// while(tdi.hasNext()) {
			// System.out.println(tdi.next());
			// }
			tdi = td.iterator();
			return Graph.makeDepGraph(tdi);
		}
		return null;
	}

	public ArrayList<Relation> extract(String sentenceString)
			throws IOException {
		ArrayList<Relation> res = new ArrayList<Relation>();
		Annotation doc = new Annotation(sentenceString);
		pipeline.annotate(doc);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
		List<CoreMap> sentences = doc.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Get dependency graph
			System.out.println(sentence);
			// Step 1 : Get the typed dependencies
			Tree tree = sentence.get(TreeAnnotation.class);

			GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
			GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);

			// Collection<TypedDependency> td = gs.typedDependenciesCollapsed();
			Collection<TypedDependency> td = gs.allTypedDependencies();
			// Collection<TypedDependency> td =
			// gs.typedDependenciesCCprocessed();
			Iterator<TypedDependency> tdi = td.iterator();

			tdi = td.iterator();
			// // Step 2 : Make a graph out of them
			Graph depGraph = Graph.makeDepGraph(tdi);

			// Step 3 : Identify all the country number word pairs
			ArrayList<Pair<Country, Number>> pairs = getPairs(depGraph,
					sentence);

			// Step 4 : Extract the relations that exists in these pairs
			res.addAll(getExtractions(depGraph, pairs, true));
		}
		return res;
	}

	public void batchExtract(String fileString, String outFile)
			throws IOException {
		Annotation doc = new Annotation(fileString);
		pipeline.annotate(doc);
		List<CoreMap> sentences = doc.get(SentencesAnnotation.class);
		int i = 1;
		int totalExtractions = 0;
		PrintWriter pw = new PrintWriter(new FileWriter(outFile));
		for (CoreMap sentence : sentences) {
			// Get dependency graph

			// Step 1 : Get the typed dependencies
			Tree tree = sentence.get(TreeAnnotation.class);
			TreebankLanguagePack tlp = new PennTreebankLanguagePack();
			GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
			GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);

			// Collection<TypedDependency> td = gs.typedDependenciesCollapsed();
			Collection<TypedDependency> td = gs.allTypedDependencies();
			// Collection<TypedDependency> td =
			// gs.typedDependenciesCCprocessed();
			Iterator<TypedDependency> tdi = td.iterator();

			// Step 2 : Make a graph out of them
			Graph depGraph = Graph.makeDepGraph(tdi);

			// Step 3 : Identify all the country number word pairs
			ArrayList<Pair<Country, Number>> pairs = getPairs(depGraph,
					sentence);

			pw.write("\n---\n");
			pw.write("sentence " + i++ + "\n");
			pw.write(sentence + "\n\n");
			System.out
					.println("\n------------------------------------------------------");
			System.out.println("\nSentence ===> " + sentence);
			// Step 4 : Extract the relations that exists in these pairs

			ArrayList<Relation> res = getExtractions(depGraph, pairs, true);
			totalExtractions += res.size();
			System.out.println("Extractions == > " + res);
			for (Relation r : res) {
				pw.write(r + "\n");
			}

			pw.write("---\n");
			// /*/System.out.println(getExtractions(depGraph, pairs) + "\n");
		}
		pw.close();
		System.out.println("Total extractions : " + totalExtractions);
	}

	public boolean isYear(String token) {
		return yearPat.matcher(token).matches();
	}

	public boolean unitRelationMatch(String rel, Pair<Country, Number> arg) {
		Unit unit = ue.quantDict.getUnitFromBaseName(arg.second.getUnit());
		if (unit != null && !unit.getBaseName().equals("")) {
			Unit SIUnit = unit.getParentQuantity().getCanonicalUnit();
			if (SIUnit != null
					&& !RelationMetadata.getUnit(rel).equals(
							SIUnit.getBaseName())
					|| SIUnit == null
					&& !RelationMetadata.getUnit(rel)
							.equals(unit.getBaseName())) {
				return false; // Incorrect unit, this cannot be the
								// relation.
			}
		} else if (unit == null && !arg.second.getUnit().equals("")
				&& RelationMetadata.getUnit(rel).equals(arg.second.getUnit())) { // for
																					// the
																					// cases
																					// where
																					// units
																					// are
																					// compound
																					// units.
			return true;
		} else {
			if (!RelationMetadata.getUnit(rel).equals("")) {
				return false; // this cannot be the correct relation.
			}
		}
		return true;
	}

	ArrayList<Relation> getExtractions(Graph depGraph,
			ArrayList<Pair<Country, Number>> pairs, boolean augmentPhrases)
			throws IOException {
		ArrayList<Relation> result = new ArrayList<Relation>();
		HashMap<Pair<Word, Word>, Relation> alreadyExtractedRelMap = new HashMap<Pair<Word, Word>, Relation>();

		// The hashcode of Relation does not include argument2
		for (Pair<Country, Number> pair : pairs) {
			// System.out.println("\nPair == > " + pair);
			// System.out.println(depGraph.getWordsOnPath(pair.country,
			// pair.number));
			ArrayList<Word> wordsOnDependencyGraphPath = depGraph
					.getWordsOnPath(pair.first, pair.second);
			// System.out.println("Path == > " + wordsOnDependencyGraphPath);
			ArrayList<Relation> rels = ExtractFromPath.getExtractions(pair,
					wordsOnDependencyGraphPath, depGraph);
			for (Relation rel : rels) {

				if (unitsActive) {
					if (!unitRelationMatch(rel.getRelName(), pair)) {
						continue; // if unit doesn't match, try next relation.
					}
				}
				if (augmentPhrases) {
					rel = augment(depGraph, rel);
				}
				Pair<Word, Word> argRelPairKey = new Pair<Word, Word>(
						rel.getCountry(), rel.getKeyword());
				if (alreadyExtractedRelMap.containsKey(argRelPairKey)) { // the
																			// same
																			// arg1,
																			// relation,
																			// and
																			// keyword
																			// have
																			// already
																			// been
																			// extracted?
					Number prevNumber = alreadyExtractedRelMap.get(
							argRelPairKey).getNumber();
					Number currNumber = rel.getNumber();
					if (depGraph.distance(rel.getCountry(), currNumber) < depGraph
							.distance(rel.getCountry(), prevNumber)) { // the
																		// current
																		// number
																		// is
																		// closer?
						alreadyExtractedRelMap.put(argRelPairKey, rel);
					} // else nothing to do, the relation already present in the
						// map is the one that should be there
				} else { // new relation, must extract
					alreadyExtractedRelMap.put(argRelPairKey, rel);
				}
			}
		}
		for (Relation rel : alreadyExtractedRelMap.values()) {
			result.add(rel);
		}
		return result;
	}

	/**
	 * This is the workhorse, given a relation, checks if the argument or the
	 * relation can be augmented, and if so, returns the augmented relation
	 * 
	 * @param rel
	 * @return
	 */
	private static Relation augment(Graph depGraph, Relation rel) {
		/* Augment the argument */

		boolean hasChaged = false;

		Word countryArg = rel.getCountry();
		HashSet<Word> modifiers = null;

		/*
		 * Augmenting country first
		 */
		StringBuffer countryValBuffer = new StringBuffer("");
		countryValBuffer.append(rel.getCountry().getVal());

		if (null != (modifiers = depGraph.getRelationModifiers(countryArg))) {
			hasChaged = true;
			for (Word modifier : modifiers) {
				// modifier.getVal() + " " + arg1.getVal());
				countryValBuffer.append(" " + modifier.getVal());

			}
		}

		/* Augment Relation */
		Word relWord = rel.getKeyword();
		StringBuffer relValBuffer = new StringBuffer("");
		relValBuffer.append(rel.getCountry().getVal());
		modifiers = null;
		if (null != (modifiers = depGraph.getRelationModifiers(relWord))) {
			hasChaged = true;
			for (Word modifier : modifiers) {
				// relWord.setVal(modifier.getVal() + " " + relWord.getVal());
				relValBuffer.append(" " + modifier.getVal());
			}
		}

		if (hasChaged) { // need to create a new relation
			Word newCountry = new Word(rel.getCountry().getIdx(),
					countryValBuffer.toString());
			Word newRelWord = new Word(rel.getKeyword().getIdx(),
					relValBuffer.toString());
			Relation newRel = new Relation(newCountry, rel.getNumber(),
					newRelWord, rel.getRelName());
			return newRel;
		}
		return rel;
	}

	public boolean isCountry(String token) {
		return countryList.contains(token.toLowerCase());
	}

	public boolean isNumber(String token) {
		return numberPat.matcher(token.toString()).matches();
	}

	public ArrayList<Pair<Country, Number>> getPairs(Graph depGraph,
			String sentenceString) {
		Annotation doc = new Annotation(sentenceString);
		pipeline.annotate(doc);
		// TreebankLanguagePack tlp = new PennTreebankLanguagePack();
		List<CoreMap> sentences = doc.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			return getPairs(depGraph, sentence);
		}
		return null;
	}

	/**
	 * Returns the list of country number pairs in the graph Uses the indexes
	 * defined by the dependency graph which is passed as an argument
	 * 
	 * @param depGraph
	 * @param sentence
	 * @return
	 */
	public ArrayList<Pair<Country, Number>> getPairs(Graph depGraph,
			CoreMap sentence) {
		ArrayList<Country> countries = new ArrayList<Country>();
		ArrayList<Number> numbers = new ArrayList<Number>();
		ArrayList<Pair<Country, Number>> res = new ArrayList<Pair<Country, Number>>();
		float values[][] = new float[1][1];
		int idx = 1;
		for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
			// this is the text of the token
			String tokenStr = token.get(TextAnnotation.class);
			Word currWord = depGraph.getWordForIndex(idx);
			idx++;
			// System.out.println(word + " - " + depGraph.getIdx(word) +
			// depGraph.nodeWordMap.get(depGraph.getIdx(word)));
			if (isCountry(tokenStr)) {
				countries.add(new Country(currWord.getIdx(), currWord.getVal(),
						currWord.getStartOff(), currWord.getEndOff()));
			}

			if (isNumber(tokenStr) && !isYear(tokenStr)) {

				Float num_val = Number.getDoubleValue(
						Unit.parseDecimalExpressionL(tokenStr)).floatValue();

				Number num = new Number(currWord.getIdx(), currWord.getVal(),
						currWord.getStartOff(), currWord.getEndOff());
				num.setFlatVal(num_val);
				if (unitsActive) {
					String sentString = sentence.toString();
					int beginIdx = sentString.indexOf(tokenStr);
					int endIdx = beginIdx + tokenStr.length();

					String front = sentString.substring(0, beginIdx);
					if (front.length() > 20) {
						front = front.substring(front.length() - 20);
					}
					String back = sentString.substring(endIdx);
					if (back.length() > 20) {
						back = back.substring(0, 20);
					}
					String utString = front + "<b>" + tokenStr + "</b>" + back;

					List<? extends EntryWithScore<Unit>> unitsS = ue.parser
							.getTopKUnitsValues(utString, "b", 1, 0, values);

					// check for unit here....
					if (unitsS != null && unitsS.size() > 0) {

						// setting the unit
						String curUnit = unitsS.get(0).getKey().getBaseName();
						num.setUnit(curUnit);

						// STORE FLATTENED VALUE;

						QuantityCatalog qu = ue.quantDict;

						String unit_parts[] = curUnit.split("\\["); // Looking
																	// for
																	// multiplier,
																	// e.g, sq
																	// km
																	// [million],
																	// [billion],
																	// etc.
						Unit b_unit;
						Unit multiplier = null;
						if (unit_parts.length == 1) { // no multiplier
							b_unit = qu.getUnitFromBaseName(unit_parts[0]);
						} else {
							b_unit = qu.getUnitFromBaseName(unit_parts[0]
									.trim());
							String mult = unit_parts[1].split("\\]")[0];
							multiplier = qu.getUnitFromBaseName(mult);
						}

						// flat the value and store it in num_val
						if (b_unit != null) {
							Unit SIUnit = b_unit.getParentQuantity()
									.getCanonicalUnit();
							if (SIUnit != null) {
								boolean success[] = new boolean[1];
								num_val = qu.convert(num_val, b_unit, SIUnit,
										success);
							}
						}

						if (multiplier != null
								&& multiplier.getParentQuantity() != null) {
							boolean success[] = new boolean[1];
							num_val = qu.convert(num_val.floatValue(),
									multiplier, multiplier.getParentQuantity()
											.getCanonicalUnit(), success);
						}
						num.setFlatVal(num_val);
					}
				}
				numbers.add(num);
			}

		}
		if (countries.size() == 1) { //okay to cross product if just one country is present
			for (int i = 0, lc = countries.size(); i < lc; i++) {
				for (int j = 0, ln = numbers.size(); j < ln; j++) {
					res.add(new Pair<Country, Number>(countries.get(i), numbers
							.get(j)));
				}
			}
		} else {
			for (int i = 0, lc = countries.size(); i < lc; i++) {
				Number closestNum = null;
				int closestDist = 100000;
				Country currCount = countries.get(i);
				for (int j = 0, ln = numbers.size(); j < ln; j++) {
					int dist = getDist(currCount, numbers.get(j));
					if(dist < closestDist) {
						closestNum = numbers.get(j);
						closestDist = dist;
					}
				}
				if(closestNum != null) {
					res.add(new Pair<Country, Number>(countries.get(i), closestNum));
				}
				
			}
		}
		cumulativeLen += sentence.toString().length();
		return res;
	}

	private int getDist(Country currCount, Number number) {
		Interval<Integer> countryIntr = Interval.toInterval(currCount.getStartOff(), currCount.getEndOff());
		Interval<Integer> numItr = Interval.toInterval(number.getStartOff(), number.getEndOff());
		return findIntervalDistance(countryIntr, numItr);
	}

	 
		private int findIntervalDistance(Interval<Integer> intr1,
				Interval<Integer> intr2) {
			int order = intr1.compareIntervalOrder(intr2);
			if (order == -1) { // before
				return intr2.first() - intr1.second();
			} else if (order == 1) {
				return intr1.first() - intr2.second();
			} else {
				return -1;
			}
		}
	public void setUnitsActive(boolean unitsActive) {
		this.unitsActive = unitsActive;
	}
}
