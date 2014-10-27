import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

public class MiniSearchEngine {
	private HashMap<String, HashMap<String, Integer>> docIndex = new HashMap<>();
	private static HashSet<String> stopwordsSet = new HashSet<>();
	
	public MiniSearchEngine(String docsPath) {
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
		
		buildIndex(docDir);
//		for(String k: docIndex.keySet()) {
//			System.out.println(docIndex.get(k).size() + " " + docIndex.get(k));
//		}
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
						tfMap.put(token, tfMap.get(token)+1);
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
	
	public ArrayList<String> makeQuery(String query) {
		ArrayList<String> docs = new ArrayList<>();
		return docs;
	}
}
