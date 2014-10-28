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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

public class MiniSearchEngine {
	private HashMap<String, HashMap<String, Integer>> docIndex = new HashMap<>();
	private HashMap<String, Double> idfMap = new HashMap<>();
	private HashMap<String, HashSet<String>> idfDocMap = new HashMap<>();

	private static HashSet<String> stopwordsSet = new HashSet<>();
	
	public MiniSearchEngine(String docsPath, String indexDir) {
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
		
		File indexFile = new File(indexDir + "/dd_index.txt");

		if (!indexFile.exists()) {
			buildIndex(docDir);
			buildInvertedIndex();
			System.out.println(idfMap);
			try {
				indexFile.createNewFile();
				writeIndexFile(indexFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			loadIndexFile(indexFile);
		}
	}
	
	public static void setStopWords(String stopDir) {
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
	
	/**
	 * Indexes the given file using the given writer, or if a directory is given,
	 * recurses over files and directories found under the given directory.
	 */
	private void buildIndex(File file) {		
		if (file.canRead()) {
			if (file.isDirectory()) {
				String[] files = file.list();
				if (files != null) {
					for (int i = 0; i < files.length; i++) {
						buildIndex(new File(file, files[i]));
					}
				}
			} else {
				FileInputStream fis = null;
				String fname = file.getName();
				try {
					fis = new FileInputStream(file);
				} catch (FileNotFoundException e) {
					System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
				}
				
				docIndex.put(fname, new HashMap<String, Integer>());
				HashMap<String, Integer> tfMap = docIndex.get(fname);
				
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					TokenStream stream = new StandardTokenizer(br);
					stream = new PorterStemFilter(stream);
					stream = new StopFilter(stream, new CharArraySet(stopwordsSet, true));
					stream.reset();

					while(stream.incrementToken()) {
						String token = stream.getAttribute(CharTermAttribute.class).toString().toLowerCase();
						
						if(!tfMap.containsKey(token)) {
							tfMap.put(token, 0);
						}
						
						// increment the token frequency for this doc's tfMap
						tfMap.put(token, tfMap.get(token)+1);
						
						if(!idfDocMap.containsKey(token)) {
							idfDocMap.put(token, new HashSet<String>());
						}
						
						// add this document to the inverse term set
						idfDocMap.get(token).add(fname);
					}
					stream.close();
				} catch (IOException e) {
					System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
				} finally {
					try {
						fis.close();
					} catch(IOException e) {
						System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
					}
				}
			}
		}
	}
	
	// build inverted index
	private void buildInvertedIndex() {
		int num_docs = docIndex.size();
		
		// build the idf map
		for(String token: idfDocMap.keySet()) {
			int num_docs_containing = idfDocMap.get(token).size();
			idfMap.put(token, Math.log((double) num_docs / num_docs_containing));
		}
	}
	
	private void writeIndexFile(File outputFile) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
		
		for(String doc: docIndex.keySet()) {
			bw.write(doc + ":");
			for(String word: docIndex.get(doc).keySet()) {
				if(doc.equals("MED-0507.txt")) {
					System.out.println(word + " " + docIndex.get(doc).get(word));
				}
				bw.write(word + " " + docIndex.get(doc).get(word) + ";");
			}
			bw.write("\n");
		}
		bw.close();
	}
	
	private void loadIndexFile(File indexFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(indexFile));
			String line = "";
			while((line = br.readLine()) != null) {
				String[] docToken = line.split(":");
				if(docToken.length < 2) continue;
				
				// parse document
				String docName = docToken[0].trim();
				if(!docIndex.containsKey(docName)) {
					docIndex.put(docName, new HashMap<String, Integer>());
				}
				HashMap<String, Integer> tfMap = docIndex.get(docName);
				
				// parse word,frequency tuples
				String[] tupleTokens = docToken[1].split(";");
				for(String tuple: tupleTokens) {
					String[] wordFreq = tuple.split(" ");
					if(wordFreq.length < 2) continue;
					
					String word = wordFreq[0].trim();
					int freq = Integer.parseInt(wordFreq[1].trim());
					
					if(!tfMap.containsKey(word)) {
						tfMap.put(word, 0);
					}
					tfMap.put(word, freq);
				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public List<String> makeQuery(String query) {
		List<String> docResults = new ArrayList<>();
		
		// parse query terms
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
		
		if(queryTokens.size() == 0) {
			return null;
		}
		
		// scores data structures
		HashMap<String, Double> docScores = new HashMap<>();
		ValueComparator vc = new ValueComparator(docScores);
		TreeMap<String, Double> sortedScores = new TreeMap<>(vc);
		
		// compute doc scores!
		for(String doc: docIndex.keySet()) {
			double docScore = 0;
			HashMap<String, Integer> tfMap = docIndex.get(doc);
			for(String qt: queryTokens) {
				if(!tfMap.containsKey(qt) || !idfMap.containsKey(qt)) continue;
				
				int tf = tfMap.get(qt);
				double idf = idfMap.get(qt);
				docScore += tf * idf;
			}
			docScores.put(doc, docScore);
		}
		
		// sort docs by scores
		sortedScores.putAll(docScores);
		for(String doc: sortedScores.keySet()) {
			docResults.add(doc);
		}
		
		return docResults.subList(0, 100);
	}
	
	/*
	 * For sorting hashmap elements by value
	 */
	class ValueComparator implements Comparator<String> {
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
	}}
