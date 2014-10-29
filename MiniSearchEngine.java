import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

public class MiniSearchEngine {
	private HashMap<String, HashMap<String, Double>> docIndex = new HashMap<>();
	private HashMap<String, Double> idfMap = new HashMap<>();
	private HashMap<String, HashSet<String>> idfDocMap = new HashMap<>();
	
	private double avg_doc_length = 0;
	
	private HashSet<String> stopwordsSet = new HashSet<>();
	private HashSet<String> possible_weightings = new HashSet<>(Arrays.asList("atc.atc", "atn.atn", "ann.bpn", "custom"));
	
	public MiniSearchEngine(String docsPath, String indexDir, String stopDir) {
		// Check whether docsPath is valid
		if (docsPath == null || docsPath.isEmpty()) {
			System.err.println("Document directory cannot be null");
			System.exit(1);
		}

		// Check whether the directory is readable
		final File docDir = new File(docsPath);
		if (!docDir.exists() || !docDir.canRead()) {
			System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
			System.exit(1);
		}
		
		// set stop words
		if(stopDir != null) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(new File(stopDir)));
				String line = "";
				while((line = br.readLine()) != null) {
					stopwordsSet.add(line.trim().toLowerCase());
				}
				br.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		// build index if it doesn't exist, otherwise load index from file
		File indexFile = new File(indexDir + "/dd_index.txt");
		if (!indexFile.exists()) {
			buildIndex(docDir);
			
			writeIndexFile(indexFile);
		} else {
			loadIndexFile(indexFile);
		}
	}
	
	/**
	 * Indexes the given file using the given writer, or if a directory is given,
	 * recurses over files and directories found under the given directory.
	 */
	private void buildIndex(File file) {
		buildIndexRecursive(file);
		avg_doc_length /= docIndex.size();
		
		// build the idf map
		for(String token: idfDocMap.keySet()) {
			idfMap.put(token, (double) idfDocMap.get(token).size());
		}
	}

	// Recursively builds index from files in directory
	private void buildIndexRecursive(File file) {		
		if (file.canRead()) {
			if (file.isDirectory()) {
				String[] files = file.list();
				if (files != null) {
					for (int i = 0; i < files.length; i++) {
						buildIndexRecursive(new File(file, files[i]));
					}
				}
			} else {
				String fname = file.getName();
				String docName = fname.substring(0, fname.indexOf("."));
				
				docIndex.put(docName, new HashMap<String, Double>());
				HashMap<String, Double> tfMap = docIndex.get(docName);
				
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
					TokenStream stream = new StandardTokenizer(br);
					stream = new PorterStemFilter(stream);
					stream = new StopFilter(stream, new CharArraySet(stopwordsSet, true));
					stream.reset();
					
					int num_tokens = 0;
					while(stream.incrementToken()) {
						String token = stream.getAttribute(CharTermAttribute.class).toString().toLowerCase();
						num_tokens++;
						
						// increment the token frequency for this doc's tfMap
						if(!tfMap.containsKey(token)) {
							tfMap.put(token, 0.0);
						}
						tfMap.put(token, tfMap.get(token)+1);
						
						// add this document to the inverse term set
						if(!idfDocMap.containsKey(token)) {
							idfDocMap.put(token, new HashSet<String>());
						}
						idfDocMap.get(token).add(docName);
					}
					avg_doc_length += num_tokens;
					
					stream.close();
				} catch (IOException e) {
					System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
				}
			}
		}
	}
	
	/*
	 * Write the built index to a file
	 */
	private void writeIndexFile(File outputFile) {
		try {
			outputFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

			for(String doc: docIndex.keySet()) {
				bw.write(doc + ":");
				for(String word: docIndex.get(doc).keySet()) {
					bw.write(word + " " + docIndex.get(doc).get(word).intValue() + ";");
				}
				bw.write("\n");
			}
			bw.write("IDF_START\n");
			for(String term: idfMap.keySet()) {
				bw.write(term + " " + idfMap.get(term).intValue() + "\n");
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadIndexFile(File indexFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(indexFile));
			String line = "";
			// indicates if we are parsing idf or document index
			boolean parsing_idf = false;
			while((line = br.readLine()) != null) {
				if(line.trim().equals("IDF_START")) {
					parsing_idf = true;
					continue;
				}
				
				if(parsing_idf) {
					String[] tokens = line.split(" ");
					if(tokens.length < 2) continue;
					
					idfMap.put(tokens[0].trim(), Double.parseDouble(tokens[1].trim()));
				} else {
					String[] docToken = line.split(":");
					if(docToken.length < 2) continue;
					
					// parse document
					String docName = docToken[0].trim();
					docIndex.put(docName, new HashMap<String, Double>());
					HashMap<String, Double> tfMap = docIndex.get(docName);
					
					// parse word,frequency tuples
					String[] tupleTokens = docToken[1].split(";");
					for(String tuple: tupleTokens) {
						String[] wordFreq = tuple.split(" ");
						if(wordFreq.length < 2) continue;
						
						String word = wordFreq[0].trim();
						double freq = Double.parseDouble(wordFreq[1].trim());
						tfMap.put(word, freq);
						
						avg_doc_length += freq;
					}
				}
			}
			avg_doc_length /= docIndex.size();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Tokenize the query with Lucene
	 */
	private ArrayList<String> tokenizeQuery(String query) {
		ArrayList<String> queryTokens = new ArrayList<>();
		try {
			BufferedReader br = new BufferedReader(new StringReader(query));
			TokenStream stream = new StandardTokenizer(br);
			stream = new PorterStemFilter(stream);
			stream = new StopFilter(stream, new CharArraySet(stopwordsSet, true));
			stream.reset();

			while(stream.incrementToken()) {
				String token = stream.getAttribute(CharTermAttribute.class).toString().toLowerCase();
				queryTokens.add(token);
			}
			stream.close();
		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}
		return queryTokens;
	}
	
	/*
	 * Return the maximum value of entries in a map
	 */
	private double getMaxInMap(HashMap<String, Double> map) {
		double max = 0;
		for(String token: map.keySet()) {
			double tf = map.get(token);
			if(tf > max) {
				max = tf;
			}
		}
		return max;
	}
	
	/*
	 * Return a map with token counts from the given list
	 */
	private HashMap<String, Double> countTokens(List<String> tokens) {
		HashMap<String, Double> countMap = new HashMap<>();
		for(String tok: tokens) {
			if(!countMap.containsKey(tok)) {
				countMap.put(tok, 0.0);
			}
			countMap.put(tok, countMap.get(tok) + 1);
		}
		return countMap;
	}
	
	/*
	 * Calculates tf*idf weights and returns a map
	 */
	private Vector calculateTfIdfWeights(
			HashMap<String, Double> tfMap, HashMap<String, Double> idfMap, String simMeasure, double max_tf) {
		HashMap<String, Double> weightVector = new HashMap<>();
		double norm = 0;		
		int num_docs = docIndex.size();

		for(String token: tfMap.keySet()) {
			double tf = tfMap.get(token);
			if(simMeasure.startsWith("a")) {
				tf = 0.5 + 0.5 * (tf / max_tf);
			} else if(simMeasure.equals("bpn")) {
				tf = (tf > 0 ? 1 : 0);
			}
			
			double idf = 1;
			if(idfMap.containsKey(token)) {
				idf = idfMap.get(token);
			}
			if(simMeasure.equals("bpn")) {
				idf = Math.log((num_docs - idf) / idf);
			} else if (simMeasure.equals("atc") || simMeasure.equals("atn")) {
				idf = Math.log(num_docs / idf);
			}
			
			double td_idf = tf * idf;
			weightVector.put(token, td_idf);
			
			norm += Math.pow(td_idf, 2);
		}
		return new Vector(weightVector, Math.sqrt(norm));
	}
	
	private List<String> makeQuery(String query, String simMeasure, int limit) {
		List<String> docResults = new ArrayList<>();
		
		// tokenize query terms
		ArrayList<String> queryTokens = tokenizeQuery(query);
		if(queryTokens.size() == 0) {
			return null;
		}
				
		// scores data structures
		HashMap<String, Double> docScores = new HashMap<>();
		ValueComparator vc = new ValueComparator(docScores);
		TreeMap<String, Double> sortedScores = new TreeMap<>(vc);
		
		//=========== Query calculations ===========
		// create tf map for query
		HashMap<String, Double> queryTfMap = countTokens(queryTokens);
		// find max_tf
		double query_max_tf = getMaxInMap(queryTfMap);

		// calculate query weight vector
		Vector queryVec = calculateTfIdfWeights(queryTfMap, idfMap,
				simMeasure.split("[.]")[1], query_max_tf);
		HashMap<String, Double> queryWeights = queryVec.getVector();
		double query_norm = queryVec.getNorm();
		// =====================================
		
		// compute doc scores using cosine similarity!
		double num_docs = docIndex.size();
		for(String doc: docIndex.keySet()) {
			double simScore = 0;
			
			// get tf map
			HashMap<String, Double> tfMap = docIndex.get(doc);
			// find max_tf
			double doc_max_tf = getMaxInMap(tfMap);
			
			// calculate document tf*idf's
			double doc_norm = 0;			
			for(String token: tfMap.keySet()) {
				if(!idfMap.containsKey(token)) {
					System.out.println("Token: " + token + " not found in idfMap.");
					continue;
				}

				double doc_tf = tfMap.get(token);
				if(simMeasure.startsWith("a")) {
					doc_tf = 0.5 + 0.5 * (doc_tf / doc_max_tf);
				}
				
				double idf = idfMap.get(token);
				if(simMeasure.startsWith("atc") || simMeasure.startsWith("atn")) {
					idf = Math.log(num_docs / idf);
				}
				else if(simMeasure.startsWith("ann")) {
					idf = 1;
				}
				
				double tf_idf = doc_tf * idf;
				doc_norm += Math.pow(tf_idf, 2);
				
				// increment score if query contains it, otherwise the term is just 0
				if(queryWeights.containsKey(token)) {
					simScore += tf_idf * queryWeights.get(token);
				}
			}

			if(simMeasure.equals("atc.atc") && simScore != 0) {
				// cosine normalization
				doc_norm = Math.sqrt(doc_norm);
			} else {
				doc_norm = 1;
				query_norm = 1;
			}

			docScores.put(doc, simScore/(doc_norm * query_norm));
		}
		
		// sort docs by scores
		sortedScores.putAll(docScores);
		for(String doc: sortedScores.keySet()) {
			docResults.add(doc);
		}
//		System.out.println(sortedScores);
		return docResults.subList(0, limit);
	}
	
	/*
	 * For sorting hashmap elements by value
	 */
	private class ValueComparator implements Comparator<String> {
	    HashMap<String, Double> base;
	    public ValueComparator(HashMap<String, Double> base) {
	        this.base = base;
	    }

	    // Note: this comparator imposes orderings that are inconsistent with equals.    
	    public int compare(String a, String b) {
	        if (base.get(a) >= base.get(b)) {
	            return -1;
	        } else {
	            return 1;
	        } // returning 0 would merge keys
	    }
	}
	
	/*
	 * Vector class encapsulates weight vector and norm
	 */
	private class Vector {
		private HashMap<String, Double> vector;
		double norm;
		public Vector(HashMap<String, Double> vector, double norm) {
			this.vector = vector;
			this.norm = norm;
		}
		
		public HashMap<String, Double> getVector() {
			return vector;
		}
		
		public double getNorm() {
			return norm;
		}
	}
	
	public double evaluate(Map<Integer, String> queries, Map<Integer, HashSet<String>> queryAnswers,
			int numResults, String weighting) {
//		if(possible_weightings.contains(weighting)) {
//			System.out.println("Weighting does not exist. Defaulting to atc.atc");
//			weighting = "atc.atc";
//		}
		
		// Search and evaluate
		double sum = 0;
		int num_evaluated = 0;
		for (Integer i : queries.keySet()) {
			List<String> results = makeQuery(queries.get(i), weighting, numResults);
			
//				sum += precision(queryAnswers.get(i), results);
			sum += EvaluateQueries.MAP(queryAnswers.get(i), results);
			num_evaluated++;
			
			System.out.printf("\nTopic %d  ", i);
			System.out.println(results);
//			System.out.println(queryAnswers.get(i));
//			System.out.print(EvaluateQueries.MAP(queryAnswers.get(i), results));
//			System.out.println();
		}
		System.out.println(sum/num_evaluated);
		return sum / num_evaluated;
	}
}
