package com.standalone.aem;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.standalone.aem.connect.Login;

/**
 * The Class ReadChildNodes.
 * 
 * @author sudheerdvn
 */
public class ReadChildNodes {

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 */
	public static void main(String[] args) {

		Session session = Login.getSession(null);

		if (null != session) {
			getRootPaths(session);
		}
	}

	/**
	 * Gets the root paths.
	 *
	 * @param session
	 *            the session
	 * @return the root paths
	 */
	private static void getRootPaths(Session session) {
		try {
			Node node = session.getNode("/content/aem-flash");
			if (null != node) {
				System.out.println(node.getName());
				NodeIterator nodeIterator = node.getNodes();
				while (nodeIterator.hasNext()) {
					Node childNode = nodeIterator.nextNode();
					System.out.println(childNode.getName());
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		} finally {
			session.logout();
		}

	}

}
