package licencecontrol.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ShutdownHook extends Thread{
	
	private static final String _QUERY = "http://localhost:8080/rest/licence/unregister?query=";
	private String licence;
	private String tempKey;
	private String tempKeyFilePath;
	
	/**
	 * Thread du shutdown hook qui sera lanc� � la fermeture de choco
	 * Va signaler au serveur que la session est lib�r�e puis supprimer la cl� temporaire
	 * stockee localement
	 */
	@Override
	public void run() {
		System.out.println("Fermeture de la session");
		if (getTempKey() != null) {
			URL url;
			try {
				BufferedReader rd  = null;
				StringBuilder sb = null;
				String line = null;
				String query = getLicence() + ";" + getTempKey();
	 			url = new URL(_QUERY+query);
				HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
				httpCon.setDoOutput(true);
				httpCon.setRequestMethod("GET");
				httpCon.setReadTimeout(10000);
				httpCon.connect();
				
				rd  = new BufferedReader(new InputStreamReader(httpCon.getInputStream()));
		        sb = new StringBuilder();
	
		        // R�c�ption de la r�ponse du serveur
		        while ((line = rd.readLine()) != null) {
		            sb.append(line);
		        }
		        int i = Integer.valueOf(sb.toString());
		        switch (i) {
			        case 1 : {
			        	System.out.println("Erreur du serveur de BDD");
			        	break;
			        }
			        case 2 : {
			        	System.out.println("Rejet du controle de licence lors de la fermeture de session");
			        	break;
			        }
			        case 4 : {
			        	System.out.println("Session liberee");
			        	// Suppression du fichier temporaire contenant la licence
			        	File MyFile = new File(getTempKeyFilePath()); 
			        	MyFile.delete(); 
			        }
		        }
			} catch (IOException e) {
				System.err.println("Erreur d'entr�es / sorties");
			}
		}
	}
	
	public String getLicence() {
		return licence;
	}
	
	public String getTempKey() {
		return tempKey;
	}
	
	public void setLicence(String l) {
		licence = l;
	}
	
	public void setTempKey(String t) {
		tempKey = t;
	}

	public String getTempKeyFilePath() {
		return tempKeyFilePath;
	}

	public void setTempKeyFilePath(String tempKeyFilePath) {
		this.tempKeyFilePath = tempKeyFilePath;
	}
}
