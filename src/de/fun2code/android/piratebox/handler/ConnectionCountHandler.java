package de.fun2code.android.piratebox.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import sunlabs.brazil.server.Handler;
import sunlabs.brazil.server.Request;
import sunlabs.brazil.server.Server;
import android.content.Intent;
import android.util.Log;
import de.fun2code.android.piratebox.Constants;
import de.fun2code.android.piratebox.PirateBoxService;


/**
 * Handler that counts user sessions and connections (MAC addresses).
 * To count user sessions the SHA1-Hash of the following combination is used:<br/>
 * {@code REMOTE_ADDRESS + USER_AGENT_STRING + DATE}
 * <br/>
 * To get the MAC address that corresponds to an IP number, the file
 * {@literal /proc/net/arp} is inspected.
 * 
 * @author joschi
 *
 */
public class ConnectionCountHandler implements Handler {
	private String prefix;
	private static final String ARP_FILE = "/proc/net/arp";
	
	private static Set<String> sha1Hashes = new HashSet<String>();
	private static Set<String> macAddresses = new HashSet<String>();

	@Override
	public boolean init(Server server, String prefix) {
		this.prefix = prefix;
		sha1Hashes.clear();
		macAddresses.clear();
		
		return true;
	}

	@Override
	public boolean respond(Request request) throws IOException {
		String sha1 = getSha1Hash(request);

		/*
		 * If cookie is not in list of cookies the file /proc/net/arp
		 * has to be inspected.
		 */
		if(sha1 != null && !sha1Hashes.contains(sha1)) {
			sha1Hashes.add(sha1);
			// Send new session broadcast
			Intent intentSession = new Intent(Constants.BROADCAST_INTENT_CONNECTION);
			intentSession.putExtra(Constants.INTENT_CONNECTION_EXTRA_NUMBER, sha1Hashes.size());
			PirateBoxService.getService().sendBroadcast(intentSession);
			
			String mac = getMacAddress(request);
			
			/*
			 * If MAC address is not in list, add it an send a broadcast
			 */
			if(mac != null && !macAddresses.contains(mac)) {
				macAddresses.add(mac);
				Intent intentConnection = new Intent(Constants.BROADCAST_INTENT_SESSION);
				intentConnection.putExtra(Constants.INTENT_SESSION_EXTRA_NUMBER, macAddresses.size());
				PirateBoxService.getService().sendBroadcast(intentConnection);
			}
			
			// TODO: Write data to db
		}
		return false;
	}
	
	/**
	 * Returns the SHA-1 hash of the following information:<br/>
	 * {@code REMOTE_ADDRESS + USER_AGENT_STRING + DATE}
	 * <br/>
	 * This is done the same was as in LibraryBoy.
	 * <br/>
	 * see: {@link https://github.com/LibraryBox-Dev/LibraryBox-core/blob/master/customization/www_librarybox/vc_counter.php}
	 * 
	 * @param request
	 * @return
	 */
	private String getSha1Hash(Request request) {
		String remoteAddr = request.getSocket().getInetAddress().getHostAddress();
		String userAgent = request.headers.get("User-Agent");
		String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
		
		return sha1(remoteAddr + userAgent + date);
	}
	
	/**
	 * Calculates SHA-1 hash
	 * <br/>
	 * Code taken from: {@link http://www.androidsnippets.com/sha-1-hash-function}
	 * 
	 * @param s		String to use for hash calculation
	 * @return		SHA-1 hex value
	 */
	public String sha1(String s) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			Log.e(Constants.TAG, e.toString());
		}
		digest.reset();
		byte[] data = digest.digest(s.getBytes());
		return String.format("%0" + (data.length * 2) + "X", new BigInteger(1,
				data));
	}

	
	/**
	 * Retrieves the IP number of the request and returns the matching MAC
	 * address by inspecting /proc/net/arp.
	 * If no MAC address can be found {@code null} is returned.
	 * 
	 * @param request	Request to use
	 * @return			MAC address or {@code null} if the MAC address could not be found
	 */
	private String getMacAddress(Request request) {
		String mac = null;
		BufferedReader br = null;
		try {
			String ip = request.sock.getInetAddress().toString().replaceAll("[^1-9.]", "");
			File file = new File(ARP_FILE);
			br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
			  if(line.matches("^" + ip + "\\s+.*$")) {
			     mac = line.split("\\s+")[3];
			     break;
			  }
			}
		}
		catch(Exception e) {
			Log.e(Constants.TAG, "Can't get MAC address: " + e.toString());
		}
		finally {
			if(br != null) {
				try {
					br.close();
				} catch (IOException e) {
					Log.e(Constants.TAG, e.toString());
				}
			}
		}
		return mac;
	}
	
	/**
	 * Returns the current total connection count
	 * 
	 * @return		number of total connections
	 */
	public static int getConnectionCount() {
		return macAddresses.size();
	}
	
	/**
	 * Returns the current total session count
	 * 
	 * @return		number of total sessions
	 */
	public static int getSessionCount() {
		return sha1Hashes.size();
	}

}