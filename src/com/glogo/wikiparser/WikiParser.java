package com.glogo.wikiparser;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Main Wiki pages parser.
 * This class contains methods necessary to parse input XML doc, find alternative titles for articles (pages) and export them to readable format.
 * @author Glogo
 */
public class WikiParser {

	/**
	 * This regular expression pattern is used to match links in wiki article text content.<br />
	 * Only links with following rules are matched:
	 * <ul>
	 *     <li>Match only links to internal articles</li>
	 *     <li>Match only links having delimiter "|"</li>
	 *     <li>Match links in two groups</li>
	 * </ul>
	 * Unescaped pattern: "\[\[([^\]\[:]+)\|([^\]\[:]+)\]\]"<br />
	 * Short pattern description: Captures wiki links between [[]] tags not beginning with any "Namespace:" and containing "|" delimiter (result is in 2 groups) 
	 * @see <a href="http://stackoverflow.com/questions/26010846/regex-match-wikipedia-internal-article-links/26010910#26010910">Related stackoverflow question</a>
	 */
	private static final Pattern WIKI_LINKS_PATTERN = Pattern.compile("\\[\\[([^\\]\\[:]+)\\|([^\\]\\[:]+)\\]\\]");
	
	/**
	 * Wikipedia dump XML reader
	 */
	private WikiReader wikiReader = new WikiReader();
	
	/**
	 * All pages stored in TreeMap with case insensitive keys.<br />
	 *     <b>key:</b> PageModel title<br />
	 *     <b>value:</b> PageModel instance
	 */
	private Map<String, PageModel> pages = new TreeMap<String, PageModel>(String.CASE_INSENSITIVE_ORDER);
	
	/**
	 * Reads XML file as {@link InputStream} using {@link WikiReader} class, creates {@link PageModel} instances and stores them {@link WikiParser#pages} map
	 * @throws XMLStreamException 
	 * @throws IOException 
	 */
	public void readPages(String filename) throws XMLStreamException, IOException {
		wikiReader.readFile(filename, pages);
	}
	
	/**
	 * This method searches for alternative titles in pages stored in {@link WikiParser#pages} map.<br />
	 * Alternative titles will be then stored in each page property {@link PageModel#alternativeTitles}.<br />
	 * Alternative page titles are:
	 * <ul>
	 *     <li>Titles of auto redirect pages</li>
	 *     <li>Text content of links to another pages</li>
	 * </ul>
	 */
	public void findAlternativeTitles(){
		Matcher matcher;
		String matchedArticleTitle;
		String matchedLinkText;
		PageModel pageModel;
		PageModel tmpPageModel;
		
		System.out.println("Finding alternative titles");
		
		// Clear all alternative titles & anchor texts
		for (Map.Entry<String, PageModel> entry : pages.entrySet()) {
			entry.getValue().getAlternativeTitles().clear();
			entry.getValue().getAnchorTexts().clear();
		}
		
		// Loop through pages in map
		for (Map.Entry<String, PageModel> entry : pages.entrySet()) {
			pageModel = entry.getValue();
			
			// Add redirectsToPage instance if page is redirecting to another page. If redirected page was not found then null will be returned automatically
			if(pageModel.getRedirectsToPageTitle() != null){
				pageModel.setRedirectsToPage(pages.get(pageModel.getRedirectsToPageTitle()));
			}
			
			/*
			 *  1. Add alternative title to redirected page
			 */
			if(pageModel.getRedirectsToPage() != null){
				pageModel.getRedirectsToPage().addAlternativeTitle(entry.getKey());
				continue;
			}
			
			/*
			 *  2. Parse page text and extract anchor texts from links.
			 */
			// Check if page is not redirection & has not null text
			if(pageModel.getRedirectsToPageTitle() == null && pageModel.getText() != null){
				// System.out.println(pageModel.getTitle());
				
				// Find all links in page text
				matcher = WIKI_LINKS_PATTERN.matcher(pageModel.getText());
				
				// For each non-category link matches
				while(matcher.find()){
					matchedArticleTitle = matcher.group(1);
					matchedLinkText = matcher.group(2);
					//System.out.printf("%s|%s => %s\n", matchedArticleTitle, matchedLinkText, matcher.group());
					
					// Check if linked page exists in processed pages
					tmpPageModel = pages.get(matchedArticleTitle);
					if(tmpPageModel != null){
						
						//System.out.printf("%s|%s => %s\n", matchedArticleTitle, matchedLinkText, matcher.group());
						
						// Add anchor text to anchor texts if not already exists
						if(!tmpPageModel.getAnchorTexts().contains(matchedLinkText)){
							tmpPageModel.addAnchorText(matchedLinkText);
						}
					}
				}
			}			
			
		}
	}
	
	/**
	 * Adds following statistics to root of supplied json object: <br />
	 * <ul>
	 *     <li><b>pagesCnt: </b>Total pages count</li>
	 *     <li><b>redirPagesCnt: </b>Total pages count with at least one alternative title</li>
	 *     <li><b>pagesAltCnt: </b>Total pages count with at least one alternative title</li>
	 *     <li><b>pagesAnchCnt: </b>Total pages count with at least one anchor text</li>
	 *     <li><b>altTitlesCnt: </b>Total count of all alternative titles</li>
	 *     <li><b>anchTextsCnt: </b>Total count of all anchor texts</li>
	 * </ul>
	 * 
	 * @param json
	 */
	private void addMetricsToJSON(Map<String, Object> json) {
		int redirPagesCnt = 0;
		int excludedPagesCnt = 0;
		int pagesAltCnt = 0;
		int pagesAnchCnt = 0;
		int altTitlesCnt = 0;
		int anchTextsCnt = 0;
		
		PageModel pageModel;
		
		// Loop through all pages and increment appropriate metric
		for (Map.Entry<String, PageModel> entry : pages.entrySet()) {
			pageModel = entry.getValue();
			
			// If page is excluded
			if(pageModel.isExcluded()){
				excludedPagesCnt++;
			}
			
			// If page is redirect
			if(pageModel.getRedirectsToPageTitle() != null){
				redirPagesCnt++;
				continue;
			}
			
			// If page has at least one alternative title
			if(pageModel.getAlternativeTitles().size() > 0){
				pagesAltCnt++;
				altTitlesCnt += pageModel.getAlternativeTitles().size();
			}
			
			// If page has at least one anchor text
			if(pageModel.getAnchorTexts().size() > 0){
				pagesAnchCnt++;
				anchTextsCnt += pageModel.getAnchorTexts().size();
			}
		}
		
		// Store metrics values in json
		json.put("pagesCnt", pages.size());
		json.put("redirPagesCnt", redirPagesCnt);
		json.put("excludedPagesCnt", excludedPagesCnt);
		json.put("pagesAltCnt", pagesAltCnt);
		json.put("pagesAnchCnt", pagesAnchCnt);
		json.put("altTitlesCnt", altTitlesCnt);
		json.put("anchTextsCnt", anchTextsCnt);
	}
	
	public void exportToJSON(String path) throws IOException{
		
		PageModel pageModel;
		
		// LinkedHashMap is used to preserve root attributes order
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		
		// Info json element
		Map<String, Object> info = new LinkedHashMap<String, Object>();
		info.put("author", "Michael Gloger");
		addMetricsToJSON(info);
		
		json.put("info", info);
		
		// Initialize json array of all pages (it is list of pages maps)
		List<Map<String, Object>> pagesObjects = new ArrayList<Map<String, Object>>();
		json.put("pages", pagesObjects);
		
		System.out.println("Exporting pages with alternative titles to JSON");
		
		// Loop through pages and create + add element to pages element array value
		for (Map.Entry<String, PageModel> entry : pages.entrySet()) {
			
			pageModel = entry.getValue();
			
			// Skip excluded pages
			if(!pageModel.isExcluded()) {
				
				// Create alternative titles json array
				List<String> alternativeTitles = new ArrayList<String>();
	
				// Add all alternative titles to array
				for(String alternativeTitle : pageModel.getAlternativeTitles()){
					alternativeTitles.add(alternativeTitle);
				}
				
				// Create anchor texts json array
				List<String> anchorTexts = new ArrayList<String>();
				
				// Add all anchor texts to array
				for(String anchorText : pageModel.getAnchorTexts()){
					anchorTexts.add(anchorText);
				}
				
				// Create page json object
				Map<String, Object> pageObject = new HashMap<String, Object>();
				pageObject.put("title", pageModel.getTitle());
				pageObject.put("alternative", alternativeTitles);
				pageObject.put("anchor", anchorTexts);
				
				// Add page object to json
				pagesObjects.add(pageObject);
			}
		}
		
		FileWriter file = new FileWriter(path);
        try {
        	
        	// Create Google Gson to simplify json serializing
        	Gson gson = new GsonBuilder().setPrettyPrinting().create();
        	
        	// Create javascript compatibile output to enable easy querying
            file.write("var pagesData = " + gson.toJson(json) + ";");
            System.out.printf("Successfully saved JSON object to file: '%s'\n", path);
 
        } catch (IOException e) {
            e.printStackTrace();
 
        } finally {
            file.flush();
            file.close();
        }
	}
	
	public Map<String, PageModel> getPages(){
		return pages;
	}
}
