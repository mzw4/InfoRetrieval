import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.core.StopAnalyzer;
// import lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

public class EvaluateQueries {
	public static void main(String[] args) {
		String cacmDocsDir = "data/cacm"; // directory containing CACM documents
		String medDocsDir = "data/med"; // directory containing MED documents
		
		String cacmIndexDir = "data/index/cacm"; // the directory where index is written into
		String medIndexDir = "data/index/med"; // the directory where index is written into
		
		String cacmQueryFile = "data/cacm_processed.query";    // CACM query file
		String cacmAnswerFile = "data/cacm_processed.rel";   // CACM relevance judgements file

		String medQueryFile = "data/med_processed.query";    // MED query file
		String medAnswerFile = "data/med_processed.rel";   // MED relevance judgements file
		
		String stopWordsDir = "data/stopwords/stopwords_indri.txt"; // stopwords directory
				
		int cacmNumResults = 100;
		int medNumResults = 100;

	    CharArraySet stopwords = new CharArraySet(0, false);
//	    stopwords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
	    
		System.out.println(evaluate(cacmIndexDir, cacmDocsDir, cacmQueryFile,
				cacmAnswerFile, cacmNumResults, stopwords));
		System.out.println("\n");
		System.out.println(evaluate(medIndexDir, medDocsDir, medQueryFile,
				medAnswerFile, medNumResults, stopwords));
	    
//	    MiniSearchEngine.setStopWords(stopWordsDir);
//	    MiniSearchEngine cacm_se = new MiniSearchEngine(cacmDocsDir);
//	    MiniSearchEngine med_se = new MiniSearchEngine(medDocsDir);
	}

	private static Map<Integer, String> loadQueries(String filename) {
		HashMap<Integer, String> queryIdMap = new HashMap<Integer, String>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(
					new File(filename)));
		} catch (FileNotFoundException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}

		String line;
		try {
			while ((line = in.readLine()) != null) {
				int pos = line.indexOf(',');
				queryIdMap.put(Integer.parseInt(line.substring(0, pos)), line
						.substring(pos + 1));
			}
		} catch(IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		} finally {
			try {
				in.close();
			} catch(IOException e) {
				System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
			}
		}
		return queryIdMap;
	}

	private static Map<Integer, HashSet<String>> loadAnswers(String filename) {
		HashMap<Integer, HashSet<String>> queryAnswerMap = new HashMap<Integer, HashSet<String>>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(
					new File(filename)));

			String line;
			while ((line = in.readLine()) != null) {
				String[] parts = line.split(" ");
				HashSet<String> answers = new HashSet<String>();
				for (int i = 1; i < parts.length; i++) {
					answers.add(parts[i]);
				}
				queryAnswerMap.put(Integer.parseInt(parts[0]), answers);
			}
		} catch(IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		} finally {
			try {
				in.close();
			} catch(IOException e) {
				System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
			}
		}
		return queryAnswerMap;
	}

	private static double precision(HashSet<String> answers,
			List<String> results) {
		double matches = 0;
		for (String result : results) {
			if (answers.contains(result))
				matches++;
		}

		return matches / results.size();
	}
	
	/*
	 * Perform Mean Average Precision evaluation
	 */
	private static double MAP(HashSet<String> answers,
			List<String> results) {
		double matches = 0;
		double precision_sum = 0;
		for (int i = 0; i < Math.min(answers.size(), results.size()); i++) {
			String result = results.get(i);
			if (answers.contains(result)) {
				matches++;
				precision_sum += matches/(i+1);
			}
		}
		
		return precision_sum / answers.size();
	}

	private static double evaluate(String indexDir, String docsDir,
			String queryFile, String answerFile, int numResults,
			CharArraySet stopwords) {

		// Build Index
		IndexFiles.buildIndex(indexDir, docsDir, stopwords);

		// load queries and answer
		Map<Integer, String> queries = loadQueries(queryFile);
		Map<Integer, HashSet<String>> queryAnswers = loadAnswers(answerFile);

		// Search and evaluate
		double sum = 0;
		int num_evaluated = 0;
		for (Integer i : queries.keySet()) {
			List<String> results = SearchFiles.searchQuery(indexDir, queries
					.get(i), numResults, stopwords);
			
//				sum += precision(queryAnswers.get(i), results);
			sum += MAP(queryAnswers.get(i), results);
			num_evaluated++;
			
			System.out.printf("\nTopic %d  ", i);
			System.out.println(results);
			System.out.print(MAP(queryAnswers.get(i), results));
			System.out.println();
		}

		return sum / num_evaluated;
	}
}
