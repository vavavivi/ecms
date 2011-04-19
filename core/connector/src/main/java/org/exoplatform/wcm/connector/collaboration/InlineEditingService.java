package org.exoplatform.wcm.connector.collaboration;

import java.io.FileNotFoundException;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.PortalContainerInfo;
import org.exoplatform.ecm.utils.text.Text;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.services.resources.ResourceBundleService;
import org.w3c.dom.Element;

import java.security.AccessControlException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.ws.rs.core.CacheControl;
import javax.xml.transform.dom.DOMSource;
import javax.ws.rs.core.MediaType;



/**
 * Created by The eXo Platform SEA
 * Author : Ha Quang Tan
 * tan.haquang@exoplatform.com
 * Mar 23, 2011
 */
@Path("/contents/editing/")
public class InlineEditingService implements ResourceContainer{
	private static Log log = ExoLogger.getLogger(InlineEditingService.class);
	final static public String EXO_TITLE 								= "exo:title".intern();
	final static public String EXO_SUMMARY 							= "exo:summary".intern();
	final static public String EXO_TEXT		 							= "exo:text".intern();

	final static public String EXO_RSS_ENABLE 					= "exo:rss-enable".intern();	
	public final static String POST_EDIT_CONTENT_EVENT 	= "CmsService.event.postEdit".intern();
	private final String localeFile = "locale.portlet.i18n.WebUIDms".intern();
	/**
	 * SERVICE: Edit title of document.
	 *
	 * @param newTitle the new title of document
	 * @param repositoryName the repository name
	 * @param workspaceName the workspace name
	 * @param nodeUIID the UIID of node
	 * @param siteName the site name
	 *
	 * @return the response
	 *
	 * @throws Exception the exception
	 */
	@POST
	@Path("/title/")
	public Response editTitle(@FormParam("newValue") String newTitle,
			@QueryParam("repositoryName") String repositoryName,
			@QueryParam("workspaceName") String workspaceName,
			@QueryParam("nodeUIID") String  nodeUIID,
			@QueryParam("siteName") String  siteName,
			@QueryParam("language") String  language){
		return modifyProperty(EXO_TITLE, newTitle, repositoryName, workspaceName, nodeUIID, siteName, language);
	}

	/**
	 * SERVICE: Edit summary of document.
	 *
	 * @param newSummary the new summary of document
	 * @param repositoryName the repository name
	 * @param workspaceName the workspace name
	 * @param nodeUIID the UIID of node
	 * @param siteName the site name
	 *
	 * @return the response
	 *
	 * @throws Exception the exception
	 */
	@POST
	@Path("/summary/")
	public Response editSummary(@FormParam("newValue") String newSummary,
			@QueryParam("repositoryName") String repositoryName,
			@QueryParam("workspaceName") String workspaceName,
			@QueryParam("nodeUIID") String  nodeUIID,
			@QueryParam("siteName") String  siteName,
			@QueryParam("language") String  language){
		return modifyProperty(EXO_SUMMARY, newSummary, repositoryName, workspaceName, nodeUIID, siteName, language);
	}
	/**
	 * SERVICE: Edit summary of document.
	 *
	 * @param newSummary the new summary of document
	 * @param repositoryName the repository name
	 * @param workspaceName the workspace name
	 * @param nodeUIID the UIID of node
	 * @param siteName the site name
	 *
	 * @return the response
	 *
	 * @throws Exception the exception
	 */
	@POST
	@Path("/text/")
	public Response editText(@FormParam("newValue") String newText,
			@QueryParam("repositoryName") String repositoryName,
			@QueryParam("workspaceName") String workspaceName,
			@QueryParam("nodeUIID") String  nodeUIID,
			@QueryParam("siteName") String  siteName,
			@QueryParam("language") String  language){
		return modifyProperty(EXO_TEXT, newText, repositoryName, workspaceName, nodeUIID, siteName, language);
	}
	/**
	 * SERVICE: Edit value of any property
	 *
	 * @param newSummary the new summary of document
	 * @param repositoryName the repository name
	 * @param workspaceName the workspace name
	 * @param nodeUIID the UIID of node
	 * @param siteName the site name
	 *
	 * @return the response
	 *
	 * @throws Exception the exception
	 */
	@POST
	@Path("/property/")
	public Response editProperty( @QueryParam("propertyName") String propertyName,
			@FormParam("newValue") String newValue,
			@QueryParam("repositoryName") String repositoryName,
			@QueryParam("workspaceName") String workspaceName,
			@QueryParam("nodeUIID") String  nodeUIID,
			@QueryParam("siteName") String  siteName,
			@QueryParam("language") String  language){
		String decodedPropertyName =  Text.unescapeIllegalJcrChars(propertyName);
		return modifyProperty(decodedPropertyName, newValue, repositoryName, workspaceName, nodeUIID, siteName, language);
	}

	/**
	 * Edit generic property of document.
	 * @param propertyName property that need to edit
	 * @param newValue the new 'requested property' of document
	 * @param repositoryName the repository name
	 * @param workspaceName the workspace name
	 * @param nodeUIID the UIID of node
	 * @param siteName the site name
	 *
	 * @return the response
	 *
	 * @throws Exception the exception
	 */
	public Response modifyProperty(String propertyName, String newValue, String repositoryName, String workspaceName,
			String nodeUIID,String siteName, String language){
		ResourceBundle resourceBundle = null;
		String messageKey = "";
		String message = "";
		Document document = null;
		Element localeMsg = null;
		try {
			Locale locale = new Locale(language);
			ResourceBundleService resourceBundleService = WCMCoreUtils.getService(ResourceBundleService.class);
			resourceBundle = resourceBundleService.getResourceBundle(localeFile, locale);
		} catch(Exception ex) {
			log.error("Error when perform create ResourceBundle: ", ex);
		}
		try {
			document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch(Exception ex) {
			log.error("Error when perform create Document object: ", ex);
		}
		CacheControl cacheControl = new CacheControl();
		cacheControl.setNoCache(true);
		cacheControl.setNoStore(true);
		try {
			SessionProvider sessionProvider = WCMCoreUtils.getUserSessionProvider();
			RepositoryService repositoryService = (RepositoryService)ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(RepositoryService.class);
			ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
			Session session = sessionProvider.getSession(workspaceName, manageableRepository);		    
			try {
				localeMsg = document.createElement("bundle");
				Node node = (Node)session.getNodeByUUID(nodeUIID);
				node = (Node)session.getItem(node.getPath());
				if(canSetProperty(node)) {
					if (!sameValue(newValue, node, propertyName)) {
						if (newValue.length() > 0) {
							newValue = Text.unescapeIllegalJcrChars(newValue.trim());
							ExoContainer container = ExoContainerContext.getCurrentContainer();
							PortalContainerInfo containerInfo = (PortalContainerInfo)container.getComponentInstanceOfType(PortalContainerInfo.class);
							String containerName = containerInfo.getContainerName();		    	    	    
							ListenerService listenerService = WCMCoreUtils.getService(ListenerService.class, containerName);
							if (propertyName.equals(EXO_TITLE)) {
								if (!node.hasProperty(EXO_TITLE))
									node.addMixin(EXO_RSS_ENABLE);
							}
							if (!propertyName.contains("/")) {
  							node.setProperty(propertyName, newValue);
  							node.save();
							}else {
							  int iSlash = propertyName.lastIndexOf("/");
							  String subnodePath = propertyName.substring(0, iSlash);
							  String subnodeProperty = propertyName.substring(iSlash+1);
							  Node subnode = node.getNode(subnodePath);
							  subnode.setProperty(subnodeProperty, newValue);
							  subnode.save();
							  node.save();
							}
							ConversationState conversationState = ConversationState.getCurrent();
							conversationState.setAttribute("siteName", siteName);
							listenerService.broadcast(POST_EDIT_CONTENT_EVENT, null, node);
							session.save();
						}
					}
				}else {
						messageKey = "AccessDeniedException.msg";
						message = resourceBundle.getString(messageKey);
						localeMsg.setAttribute("message", message);
						document.appendChild(localeMsg);
						return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();
					}				
			} catch (AccessDeniedException ace) {
				ace.printStackTrace();
				log.error("AccessDeniedException: ", ace);
				messageKey = "AccessDeniedException.msg";
				message = resourceBundle.getString(messageKey);
				localeMsg.setAttribute("message", message);
				document.appendChild(localeMsg);
				return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();
			} catch (FileNotFoundException fie) {
				fie.printStackTrace();
				log.error("FileNotFoundException: ", fie);
				messageKey = "ItemNotFoundException.msg";
				message = resourceBundle.getString(messageKey);
				localeMsg.setAttribute("message", message);
				document.appendChild(localeMsg);
				return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();	    
			}  catch (LockException lockex) {
				lockex.printStackTrace();
				log.error("LockException", lockex);
				messageKey = "LockException.msg";
				message = resourceBundle.getString(messageKey);
				localeMsg.setAttribute("message", message);
				document.appendChild(localeMsg);
				return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Error when perform edit title: ", e);
			messageKey = "UIPresentation.label.Exception";
			message = resourceBundle.getString(messageKey);
			localeMsg.setAttribute("message", message);
			document.appendChild(localeMsg);
			return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();
		} 		
		localeMsg.setAttribute("message", "OK");
		document.appendChild(localeMsg);
		return Response.ok(new DOMSource(document), MediaType.TEXT_XML).cacheControl(cacheControl).build();
	}
	/**
	 * Compare new value with current value property
	 *
	 * @param newValue the new value of property
	 * @param node the document node
	 * 
	 * @return the result of compare
	 * 
	 * @throws Exception the exception
	 */
	private boolean sameValue(String newValue, Node node, String propertyName) throws Exception {	      
		if (!node.hasProperty(propertyName))
			return (newValue == null || newValue.length() == 0);
		return node.getProperty(propertyName).getString().equals(newValue);
	}
	
	/**
   * Can set property.
   *
   * @param node the node
   * @return true, if successful
   * @throws RepositoryException the repository exception
   */
  public static boolean canSetProperty(Node node) throws RepositoryException {
    return checkPermission(node,PermissionType.SET_PROPERTY);
  }
  
  private static boolean checkPermission(Node node,String permissionType) throws RepositoryException {
    try {
      ((ExtendedNode)node).checkPermission(permissionType);
      return true;
    } catch(AccessControlException e) {
      return false;
    }
  }
}