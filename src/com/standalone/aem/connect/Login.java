package com.standalone.aem.connect;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.commons.JcrUtils;

/**
 * The Class Login.
 * 
 * @author sudheerdvn
 */
public class Login {

	/**
	 * Instantiates a new login.
	 */
	private Login() {
		throw new IllegalStateException("This is a utility class for Login");
	}

	/**
	 * Gets the session.
	 *
	 * @param env
	 *            the env
	 * @return the session
	 */
	public static Session getSession(String env) {

		String userName = "admin";
		String userPassword = "admin";
		String host = "http://localhost:4502/crx/server";

		if ("dev".equals(env)) {
			host = "http://<<dev>>:4502/crx/server";
			// override username and password if it is different for environment
			// userName = "admin";
			// userCred = "devadmin";
		} else if ("qa".equals(env)) {
			host = "http://<<qa>>:4502/crx/server";
		}
		Session session = null;
		try {
			Repository repository = JcrUtils.getRepository(host);
			session = repository.login(new SimpleCredentials(userName, userPassword.toCharArray()), "crx.default");
			return session;
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return null;
	}

}