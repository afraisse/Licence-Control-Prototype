package licencecontrol.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import licencecontrol.util.Crypto;
import licencecontrol.util.ShutdownHook;

public class LicenceControl {
	private static final String _QUERY = "http://localhost:8080/rest/licence/register?query=";
	// Singleton
	private static LicenceControl licenceController = new LicenceControl();
	private final String token;
	
	private String licence;
	private String tempKeyPath;
	private String checkSum;
	private ShutdownHook shutdownHook;
	
	/**
	 * Constructeur priv� du singleton
	 * Attache un shutdownhook � l'�xecution
	 */
	private LicenceControl() {
		token = Crypto.generateToken();
		shutdownHook = new ShutdownHook();
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	/**
	 * Renvoie l'instance du controleur de licence, qui est un singleton
	 * @return
	 */
	public static LicenceControl getInstance() {
		return licenceController;
	}
	
	/**
	 * Effectue un contr�le de licence sur le serveur
	 * @throws MalformedURLException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public void controlOnServer() throws IOException {
		BufferedReader rd  = null;
		StringBuilder sb = null;
		String line = null;
		URL url = new URL(_QUERY+getData());
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
        
        final String[] response = sb.toString().split(";");
        if (response.length == 2) {
        	// Format correct
        	if (response[0].equals(getToken())) {
        		// V�rification de l'identit� du serveur et r�cup�ration de la cl� temporaire
        		// Si le serveur renvoie le token, c'est que la licence a �t� accept�e
        		// la r�ponse contient donc forc�ment une cl� temporaire
        		final String tempKey = response[1];
        		writeTempKey(tempKey);
        		shutdownHook.setTempKey(tempKey);
        	} else {
        		// L'identit� du serveur a �t� usurp�e
        		System.err.println("CHOCO -> Serveur non reconnu");
        		exit();
        	}
        	
        } else if (response.length == 1) {
        	// R�ponse en erreur ou en rejet
        	int error = Integer.valueOf(response[0]);
        	switch (error) {
        	case 0 : {
        		System.err.println("CHOCO -> Erreur du serveur de controle de licence");
        		break;
        	}
        	case 1 : {
        		System.err.println("CHOCO -> Erreur de la base de donnees des licences");
        		break;
        	}
        	case 2 : {
        		System.err.println("CHOCO -> Controle de licence refuse");
        		break;
        	}
        	case 3 : {
        		System.err.println("CHOCO -> Nombre maximum utilisateurs atteint");
        		break;
        	}
        	case 4 : {
        		System.out.println("CHOCO -> Session liberee");
        		break;
        	}
        	default : System.err.println("CHOCO -> Erreur inconnue");
        	}
        	exit();
        } else {
        	System.err.println("Reponse invalide du serveur");
        	exit();
        }
        rd.close();
	}
	
	/**
	 * Ecrit la cl� temporaire dans un fichier dans le m�me r�pertoire que celui de choco
	 * @param tempKey la cl� temporaire � stocker
	 */
	private void writeTempKey(String tempKey) {
		try {
			FileWriter fw = new FileWriter(getTempKeyPath(), false);
			fw.write(tempKey);
			fw.close();
		} catch (IOException e) {
			System.out.println("Erreur � l'�criture de la cl� temporaire");
			exit();
		}
		
	}

	/**
	 * Retourne une eventuelle cl� temporaire
	 * @return la cl�
	 * @throws IOException
	 */
	private String getTempKey() throws IOException {
		String tempKey;
		try {
			InputStream inputStream = new FileInputStream(getTempKeyPath());
			BufferedReader stream = new BufferedReader(new InputStreamReader(inputStream));
			tempKey = stream.readLine();
			stream.close();
		} catch (FileNotFoundException e) {
			tempKey = "";
		}
		
		if (tempKey == null) {
			tempKey = "";
		}
		return tempKey;
	}
	
	/**
	 * G�n�ration et cryptage des donn�es de la requ�te 
	 * @return donn�es crypt�s
	 * @throws RuntimeException
	 * @throws IOException
	 */
	private String getData() throws RuntimeException, IOException {
		String data = getLicence() + ";" + getCheckSum() + ";" + getToken();
		String tempKey = getTempKey();
		if (!tempKey.isEmpty()) {
			// S'il y a un cl� temporaire, on la concat�ne � la requ�te
			data += ";" + tempKey;
		}
		try {
			// Cryptage de la requ�te
			return Crypto.encryptData(data, Crypto.getPublicKey() );
		} catch (InvalidKeyException | NoSuchAlgorithmException
				| NoSuchPaddingException | InvalidKeySpecException
				| IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Production du checksum du jar de choco.
	 * @return la chaine repr�sentant le checkSum
	 */
	private String getCheckSum() {
		if (checkSum == null) {
	        StringBuilder sb = new StringBuilder();
	        FileInputStream fis = null;
	        try {
	            MessageDigest md = MessageDigest.getInstance("SHA-256");
	            fis = new FileInputStream(getPath());
	            byte[] dataBytes = new byte[1024];
	            int nread = 0;
	
	            while ((nread = fis.read(dataBytes)) != -1) {
	                md.update(dataBytes, 0, nread);
	            }
	            byte[] mdbytes = md.digest();
	
	            for (int i=0; i<mdbytes.length; i++) {
	            	sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100 , 16).substring(1));
	            }
	        } catch(NoSuchAlgorithmException e) {
	            e.printStackTrace();
	        } catch(IOException e) {
	            e.printStackTrace();
	        } finally {
	        	if (fis != null) {
	        		try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
	        	}
	        }
	        checkSum = sb.toString();
		}
        return checkSum;
	}
	
	/**
	 * R�cup�ration de la licence dans le fichier de licence, qui doit se trouver � c�t� du jar
	 * @return la chaine de caract�re de la licence
	 * @throws IOException exception lors de la lecture
	 */
	private String getLicence() throws IOException {
		if (licence == null) {
			InputStream inputStream = new FileInputStream(getLicencePath());
			BufferedReader stream = new BufferedReader(new InputStreamReader(inputStream));
			licence = stream.readLine();
			// Todo gerer null
			stream.close();
			// on confie la licence au shutdownhook
			shutdownHook.setLicence(licence);
		}
		return licence;
	}
	
	
	/**
	 * R�cup�ration du chemin d'acc�s au jar 
	 * @return chemin d'acc�s
	 * @throws UnsupportedEncodingException Exception de d�codage
	 */
	private String getPath() {
		// r�cup�ration du chemin d'acc�s au jar
		String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		String decodedPath = "";
		try {
			decodedPath = URLDecoder.decode(path, "UTF-8");
			decodedPath = decodedPath.replace('/', File.separatorChar);
		} catch (UnsupportedEncodingException e) {
			System.err.println("Erreur de d�codage du chemin d'acc�s au jar de Choco");
			exit();
		}
		// Suppression du premier slash
		return decodedPath.substring(1);
	}
	
	
	/**
	 * R�cup�ration du chemin d'acc�s � la licence
	 * @return chemin d'acc�s
	 */
	public String getLicencePath() {
		File file = new File(getPath());
		file.getParent();
		return (new File(getPath())).getParent() + File.separatorChar + "licence.txt";
	}
	
	/**
	 * r�cup�ration du chemin d'acc�s � la cl� temporaire
	 * @return chemin d'acc�s
	 */
	public String getTempKeyPath() {
		if (tempKeyPath == null) {
			File file = new File(getPath());
			file.getParent();
			tempKeyPath = (new File(getPath())).getParent() + File.separatorChar + "temp";
			shutdownHook.setTempKeyFilePath(tempKeyPath);
		}
		return tempKeyPath;
	}
	
	/**
	 * Getter du token
	 * @return le token g�n�r� par le client
	 */
	public String getToken() {
		return token;
	}
	
	/**
	 * Termine l'execution du programme
	 */
	public void exit() {
		// System.exit(0) peut etre desactiver dans le SecurityManager
		// Remise a l'etat par defaut du SecurityManager
		System.setSecurityManager(null);
		System.exit(0);
	}
}
