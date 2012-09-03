package au.gov.nsw.records.digitalarchive.opengov.migrator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import au.gov.nsw.records.digitalarchive.opengov.data.AgencyPublication;
import au.gov.nsw.records.digitalarchive.opengov.data.FullAgencyList;
import au.gov.nsw.records.digitalarchive.opengov.data.Keyword;
import au.gov.nsw.records.digitalarchive.opengov.data.Member;
import au.gov.nsw.records.digitalarchive.opengov.data.Publication;
import au.gov.nsw.records.digitalarchive.opengov.data.TempList;
import au.gov.nsw.records.digitalarchive.opengov.data.UploadedFile;

public class Migrator {

	private ArrayList<String> columnsDefinition = new ArrayList<String>();

	private Set<String> newAgencyNames = new HashSet<String>();
	private Set<String> newKeywords = new HashSet<String>();
	private Set<List<String>> newPublications = new HashSet<List<String>>();
	
	private Member member;
	
	private String baseDir  = "c:\\inbox\\";
	private static String uploadUser = "cassf@records.nsw.gov.au";
	private static String csvFile = "Open Gov Import.csv";
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		Migrator mg = new Migrator();
		mg.loadMember(uploadUser);
		mg.readFile(csvFile);
		mg.processNewAgenciesName();
		
		// do not import keywords
		//mg.processNewKeywordsName();
		
		mg.processPublications();

	}

	public void processNewAgenciesName(){
		System.out.println("Importing Agencies..");
		System.out.println("New Agencies:" + Arrays.toString(newAgencyNames.toArray()));
		
		SessionFactory sf = new Configuration().configure().buildSessionFactory();        	

		Session session = sf.openSession();
		Transaction tx = session.beginTransaction();

		for (String agency : newAgencyNames){
			addUniqueAgencyName(agency, session);
		}
		
		tx.commit();
		session.close();
		
		System.out.println("Imported Agencies..");
	}


	public void processNewKeywordsName(){
		System.out.println("Importing Keywords..");
		System.out.println("New Keywords:" + Arrays.toString(newKeywords.toArray()));
		for (String keyword : newKeywords){
			addUniqueKeyword(keyword);
		}
		System.out.println("Keywords Imported");
	}

	public void processPublications(){
		System.out.println("Importing Pulbications..");
		
		SessionFactory sf = new Configuration().configure().buildSessionFactory();        	
		Session session = sf.openSession();
		Transaction tx = session.beginTransaction();
		
		for (List<String> row:newPublications){
			addPublication(row, session);
		}
		
		tx.commit();
		session.close();
		System.out.println("Pulbications Imported");
	}

	private void loadMember(String user){
		member = fetchMember(user);
		System.out.println("Initalized [" + member.getLogin() + "] user as an importer user");
	}

	private void addUniqueAgencyName(String agencyName, Session session){

		
		String hql = String.format("FROM TempList T WHERE T.name = :agencyName");
		// retrieve from DB
		Query q = session.createQuery(hql);
		q.setParameter("agencyName", agencyName);
		List<TempList> tempList = q.list();

		if (tempList.size() == 0){

			// create new TempList
			TempList templ = new TempList();
			templ.setName(agencyName);
			session.persist(templ);

			// create new FullAgencyList
			FullAgencyList fal = new FullAgencyList();
			fal.setAgencyName(agencyName);
			// link to the newly created TempList
			fal.setTempId(templ.getTempListId());
			session.persist(fal);

			System.out.println("Added agency [" + agencyName + "]");
		}else{
			System.out.println("Ignored agency [" + agencyName + "] ");
		}
	}

	private void addUniqueKeyword(String keyword){

		SessionFactory sf = new Configuration().configure().buildSessionFactory();        	

		Session session = sf.openSession();
		Transaction tx = session.beginTransaction();

		String hql = String.format("FROM Keyword K WHERE K.keyword = :kw");
		// retrieve from DB
		Query q = session.createQuery(hql);
		q.setParameter("kw", keyword);
		List<Keyword> keywordList = q.list();

		if (keywordList.size() == 0){

			// create new keyword
			Keyword k = new Keyword();
			k.setKeyword(keyword);
			session.persist(k);

			System.out.println("Added keyowrd [" + keyword + "]");
		}else{
			System.out.println("Ignored keyword [" + keyword + "] ");
		}

		tx.commit();
		session.close();
	}
	
	private void addPublication(List<String> row, Session session){
		
		String title = readPublicationColumn( "ns1:title" ,row);
		String description = readPublicationColumn( "ns1:description" ,row);
		String datePublished = readPublicationColumn( "ns1:published" ,row);
//		String licensing = readPublicationColumn( "" ,row);
		String type = readPublicationColumn( "ns1:type" ,row);
		String coverage = readPublicationColumn( "ns1:coverage" ,row);
		String language = readPublicationColumn( "ns1:language" ,row); 
		String rights = readPublicationColumn( "ns1:rights" ,row);
		String totalPages = readPublicationColumn( "ns1:pages" ,row);
		
		System.out.println("Adding " + Arrays.toString(row.toArray()) + " publication" );
		
		
		Publication p = new Publication();
		p.setTitle(title);
		p.setDescription(description);
		p.setDatePublished(datePublished);
		p.setRights(rights);
		p.setType(type);
		p.setCoverage(coverage);
		p.setLanguage(language);
		p.setMember(member);
		p.setTotalPages(totalPages);
		// TODO add licensing boolean flag as "TRUE" for all publications
		
		p.setStatus("draft");
		session.persist(p);
		
		addAgencyToPublication(row, p, session);
		addFileToPublicaion(row, p, session);
	}

	private void addAgencyToPublication(List<String> row, Publication p, Session session){
		// LOOKUP ID From FullAgencyList
		String hql = "";
		Query q;
		String agency = readPublicationColumn( "ns1:agency" ,row);
		if (agency!=null && !agency.isEmpty()){
			hql = String.format("FROM FullAgencyList F WHERE F.agencyName = :agency");
			q = session.createQuery(hql);
			q.setParameter("agency", agency);
		}else{
			agency = readPublicationColumn( "BOS Agency number" ,row);
			if (agency!=null && !agency.isEmpty()){
				hql = String.format("FROM FullAgencyList F WHERE F.bosId = :agency");
				q = session.createQuery(hql);
				q.setParameter("agency", Integer.parseInt(agency));
			}else{
				System.err.println("No agency specified for this publication");
				return;
			}
		}
		// retrieve from DB
		
		List<FullAgencyList> agencyList = q.list();
		if (agencyList.size() > 0){
			// LINK at AgencyPublication
			
			AgencyPublication ap = new AgencyPublication();
			ap.setPublication(p);
			ap.setFullAgencyList(agencyList.get(0));
			session.persist(ap);	
			
			System.out.println("Adding agency to publication [" + ap.getFullAgencyList().getAgencyName() + "]");
		}else{
			// ERROR
			System.err.println("Agency not found:" + agency);
		}
	}
	private void addFileToPublicaion(List<String> row, Publication p, Session session){
		
		for (int i=1; i<=29; i++){
			String uid = readPublicationColumn( "uid"+i ,row);
			if (uid!=null && !uid.isEmpty()){
				//we need only 32 characters
				uid = uid.substring(0, 31);
				System.out.println("Adding file [" + uid + "] at [" + i + "]");
				UploadedFile uf = new UploadedFile();
				uf.setInboxUrl(baseDir + uid + File.separatorChar + "document.pdf");
				uf.setFileName("document.pdf");
				uf.setUid(uid);
				uf.setFileOrder(i);
				uf.setContentType(readPublicationColumn( "ns1:type" ,row));
				uf.setPublication(p);
				uf.setDateUploaded(new Date());
				session.persist(uf);
			}
		}
	}
	
	private Member fetchMember(String user){

		SessionFactory sf = new Configuration().configure().buildSessionFactory();        	

		Session session = sf.openSession();
		session.beginTransaction();

		String hql = String.format("FROM Member M WHERE M.login = :user");
		// retrieve from DB
		Query q = session.createQuery(hql);
		q.setParameter("user", user);
		List<Member> tempList = q.list();
		try{
			if (tempList.size() > 0){
				return tempList.get(0);
			}else{
				return null;
			}
		}finally{
			session.close();
		}
	}

	public void readFile(String file){
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));

			String strHeaders = in.readLine();
			processColumns(strHeaders);
			String strRow;
			while ((strRow = in.readLine()) != null) {
				processRow(strRow);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void processColumns(String csvColumn){
		StringTokenizer st = new StringTokenizer(csvColumn, ",");
		while(st.hasMoreTokens()){
			columnsDefinition.add(st.nextToken());
		}

		System.out.println("columns:" + Arrays.toString(columnsDefinition.toArray()));
	}

	public void processRow(String row){

		Pattern pattern = Pattern.compile("\"([^\"]*)\"|(?<=,|^)([^,]*)(?:,|$)");
		Matcher matcher = pattern.matcher(row);
		String token = "";
		
		List<String> rowlist = new ArrayList<String>(); 
		for (String col: columnsDefinition){
			matcher.find();
			token = matcher.group(1) == null ? matcher.group(2):matcher.group(1);
			rowlist.add(token);
			//System.out.println(col + " >> " + token);
			// store the un-registered Agency
			if (col.equals("ns1:agency")){
				if (!token.trim().equals("")){
					newAgencyNames.add(token);
				}
			}else if (col.equals("ns1:keywords")){
				if (!token.trim().equals("")){
					newKeywords.add(token);
				}
			}
		}
		
		newPublications.add(rowlist);
	}

	public String readPublicationColumn(String columnName, List<String> row){
		
		int index = 0;
		for (String col:columnsDefinition){
			if (col.equalsIgnoreCase(columnName)){
				return row.get(index);
			}
			index++;
		}
		
		return null;
	}

}
