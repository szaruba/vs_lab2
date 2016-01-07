package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, Runnable, INameserver {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	// cli
	private Shell shell;

	// properties
	private int registryPort;
	private String registryHost;
	private String rootId;
	private String managedDomain;

	// structure
	private Map<String, INameserver> subdomains = new ConcurrentHashMap<String, INameserver>();
	private Map<String, String> addresses = new ConcurrentHashMap<String, String>();

	// RMI
	private Registry registry;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		// initialize properties
		registryPort = config.getInt("registry.port");
		registryHost = config.getString("registry.host");
		rootId = config.getString("root_id");
		if(config.listKeys().contains("domain"))
			managedDomain = config.getString("domain");
	}

	@Override
	public void run() {
		try {
			// try to export remote object on available port
			boolean exported = false;
			INameserver thisExported = null;
			for(int i = 0; i < 7 && !exported; i++) {
				try {
					thisExported = (INameserver) UnicastRemoteObject.exportObject(this, registryPort-i);
					exported = true;
				} catch (ExportException ee) {
					// when port is already in use
				}
			}

			if(!exported) {
				System.out.println("There was no port available between " + (registryPort-6) + " and " + registryPort + " to start the nameserver on");
				return;
			}

			// start registry if root ns
			if(managedDomain == null) {

				registry = LocateRegistry.createRegistry(registryPort);
				registry.bind(rootId, thisExported);

				System.out.println("Nameserver 'root-nameserver' started");
			} else {
				registry = LocateRegistry.getRegistry(registryHost, registryPort);
				INameserver rootNs = (INameserver) registry.lookup(rootId);


				rootNs.registerNameserver(managedDomain, thisExported, thisExported);
				System.out.println("Nameserver '" + managedDomain + "' started");
			}

			// initialize shell
			shell = new Shell(componentName, userRequestStream, userResponseStream);
			shell.register(this);
			new Thread(shell).start();
		} catch (Exception e) {
			System.out.println("Nameserver could not start. Was the root nameserver started? (" + e.getLocalizedMessage() + ")");
			try {
				exit();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	@Command
	@Override
	public String nameservers() throws IOException {
		String s = "";

		List<String> list = new ArrayList<String>(subdomains.keySet());
		Collections.sort(list);

		for(String domain:list) {
			s += domain + "\n";
		}

		return s;
	}

	@Command
	@Override
	public String addresses() throws IOException {
		List<String> names = new ArrayList<String>(addresses.keySet());
		Collections.sort(names);

		String s = "";

		for(String name:names) {
			s += name + " " + addresses.get(name) + "\n";
		}

		return s;
	}

	@Command
	@Override
	public String exit() throws IOException {
		if(shell != null)
			shell.close();

		if(managedDomain == null) // if root ns
			UnicastRemoteObject.unexportObject(registry, true);

		UnicastRemoteObject.unexportObject(this, true);

		return "Bye.";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);

		Thread t = new Thread(nameserver);
		t.start();
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		int domainLength = getDomainLength(domain);
		String tld = getLastDomainPart(domain);

		if(domainLength == 1){
			if(subdomains.containsKey(tld)) {
				throw new AlreadyRegisteredException("The domain " + tld + " is already registered.");
			} else {
				subdomains.put(tld, nameserver);
				System.out.println("Registered nameserver for zone '" + tld + "'");
			}
		} else {
			if(subdomains.containsKey(tld)) {
				subdomains.get(tld).registerNameserver(chopLastDomainPart(domain), nameserver, nameserverForChatserver);
			} else {
				throw new InvalidDomainException("Namespace " + tld + " is not registered.");
			}
		}
	}

	public static int getDomainLength(String domain) {
		return domain.split("\\.").length;
	}

	public static String getLastDomainPart(String domain) {
		return domain.split("\\.")[getDomainLength(domain)-1];
	}

	public static String chopLastDomainPart(String domain) {
		return domain.substring(0, domain.lastIndexOf("."));
	}

	@Override
	public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		int domainLength = getDomainLength(username);

		if(domainLength == 1) {
			if(addresses.containsKey(username))
				throw new AlreadyRegisteredException("This user is already registered.");
			else {
				addresses.put(username, address);
				System.out.println("Registered user '" + username + "' at address " + address);
			}
		} else {
			String highestLevel = getLastDomainPart(username);

			if(subdomains.containsKey(highestLevel)){
				String remainder = chopLastDomainPart(username);

				subdomains.get(highestLevel).registerUser(remainder, address);
			} else {
				throw new InvalidDomainException("There is no nameserver hosting the domain '" + highestLevel + "'");
			}
		}
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		if(subdomains.containsKey(zone)) {
			System.out.println("Nameserver at zone '" + zone + "' was requested.");
			return subdomains.get(zone);
		} else {
			throw new RemoteException("There is no nameserver registered for zone '" + zone + "'");
		}
	}

	@Override
	public String lookup(String username) throws RemoteException {
		if(addresses.containsKey(username)) {
			System.out.println("Performing lookup for username '" + username + "'");
			return addresses.get(username);
		} else {
			throw new RemoteException("The user '" + username + "' is not registered");
		}
	}
}
