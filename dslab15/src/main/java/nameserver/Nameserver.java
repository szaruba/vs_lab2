package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;

import cli.Command;
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

	// properties
	private int registryPort;
	private String registryHost;
	private String rootId;
	private String managedDomain;

	// structure
	private ConcurrentHashMap<String, INameserver> subdomains = new ConcurrentHashMap<String, INameserver>();

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
		managedDomain = config.getString("domain");
	}

	@Override
	public void run() {
		// TODO

		// start registry if root ns
		if(managedDomain == null) {
			try {
				registry = LocateRegistry.createRegistry(registryPort);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

	}

	@Command
	@Override
	public String nameservers() throws IOException {
		String s = "";



		return s;
	}

	@Command
	@Override
	public String addresses() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Command
	@Override
	public String exit() throws IOException {
		// TODO Auto-generated method stub
		return null;
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

	}

	@Override
	public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		return null;
	}

	@Override
	public String lookup(String username) throws RemoteException {
		return null;
	}
}
