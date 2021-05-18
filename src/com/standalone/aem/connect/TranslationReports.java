// 
// Decompiled by Procyon v0.5.36
// 



import org.slf4j.LoggerFactory;
import com.day.cq.dam.api.Asset;
import com.day.cq.mailer.MessageGatewayService;

import javax.jcr.NodeIterator;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.sling.api.request.RequestParameter;

import com.cisco.wem.author.common.services.EmailNotificationService;
import com.cisco.wem.framework.wrapper.ProxySlingRepository;
import com.cisco.wem.xml.author.core.common.XMLEmailNotificationUtility;
import com.cisco.wem.xml.author.core.common.XMLRenditionConstants;
import com.cisco.wem.xml.author.language.translation.DamLanguageUtil;
import java.io.PrintWriter;
import org.json.JSONArray;
import com.adobe.cq.projects.api.ProjectManager;
import java.io.IOException;
import java.rmi.ServerException;
import org.apache.commons.io.FilenameUtils;
import com.adobe.fmdita.common.XmlGlobals;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import com.adobe.fmdita.translationentities.SyncJson;
import com.adobe.fmdita.translationentities.SyncJsonWrapper;
import java.util.Date;
import java.text.DateFormat;
import com.adobe.fmdita.common.NodeUtils;
import javax.jcr.RepositoryException;
import com.adobe.fmdita.postprocess.adapters.DataAdapter;
import com.adobe.fmdita.postprocess.adapters.ConrefAdapter;
import com.adobe.fmdita.postprocess.adapters.HrefAdapter;
import com.adobe.fmdita.postprocess.ReferenceManagement;
import org.apache.sling.api.resource.Resource;
import javax.jcr.ValueFactory;
import com.adobe.fmdita.common.PathUtils;
import java.util.Collection;
import java.util.Arrays;
import javax.jcr.Value;
import java.util.LinkedList;
import com.adobe.fmdita.profiles.ProfileCache;
import java.util.ArrayList;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.apache.commons.lang.StringUtils;
import java.util.HashMap;
//import com.adobe.fmdita.translationentities.Ref;
import com.cisco.wem.xml.author.language.translation.Ref;

import com.adobe.granite.references.ReferenceList;
import com.day.cq.commons.Externalizer;
import com.day.cq.commons.LanguageUtil;
import java.util.HashSet;
import org.apache.sling.api.resource.ResourceResolver;
import java.util.List;
import org.apache.felix.scr.annotations.Activate;
import org.apache.sling.commons.osgi.PropertiesUtil;
import javax.jcr.version.Version;
import java.util.Iterator;
import javax.jcr.version.VersionManager;
import javax.jcr.Node;
import com.adobe.fmdita.baselines.BaselineUtils;
import com.adobe.fmdita.common.MiscUtils;
import com.adobe.fmdita.baselines.BaselineRef;
import com.cisco.wem.xml.author.language.translation.MergeXML;
import com.adobe.fmdita.api.maps.MapUtilities;
import com.adobe.fmdita.baselines.Baseline;
import javax.jcr.Session;
import com.adobe.granite.references.ReferenceAggregator;
import com.day.cq.workflow.WorkflowService;
import org.apache.sling.jcr.api.SlingRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.apache.felix.scr.annotations.Reference;
import com.day.cq.wcm.api.PageManagerFactory;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

@SlingServlet(paths = { "/bin/xmltranslationreport" }, methods = { "POST" }, metatype = true)
public class TranslationReports extends SlingAllMethodsServlet
{
    @Property(boolValue = { false }, label = "Show files within submaps", description = "Show files within submaps in translation tab")
    private static final String SHOW_ALL_TRANSLATION_PROPERTY = "show.all.files";
    @Reference
    private PageManagerFactory pageManagerFactory;
    @Reference
    private com.cisco.wem.xml.author.language.translation.CustomTranslationUtils translationUtils;
    @Reference
    private com.cisco.wem.xml.author.language.translation.CustomSyncTranslateExecutor syncTranslateExecutor;
    private static final long serialVersionUID = -323297566176168896L;
    private static boolean SHOW_ALL_TRANSLATION;
    public static final String TRANSLATION_FOLDER = "/content/dam/projects/translation_output";
    private static final Logger logger;
    private final String[] tStatus;
    private final String[] cStatus;
    Map<String, String> statusMap;
    @Reference
    private ProxySlingRepository repository;
    @Reference
    private WorkflowService wfService;
    @Reference
    private ReferenceAggregator referenceAggregator;
    @org.apache.felix.scr.annotations.Reference
	private EmailNotificationService emailNotificationService;
	@org.apache.felix.scr.annotations.Reference
	private MessageGatewayService messageGatewayService;
    
    public TranslationReports() {
        this.pageManagerFactory = null;
        this.tStatus = new String[] { "Up to Date", "Out of Date", "In Progress" };
        this.cStatus = new String[] { "In Sync", "Out of Sync", "Missing copy", "In Progress" };
        this.statusMap = new HashMap<String, String>() {
            {
                this.put(TranslationReports.this.cStatus[0], "insync");
                this.put(TranslationReports.this.cStatus[1], "outofsync");
                this.put(TranslationReports.this.cStatus[2], "missingcopy");
                this.put(TranslationReports.this.cStatus[3], "inprogress");
            }
        };
        
    }
    
    private void translateBaseline(final String sourcePath, final String baselineName, final String destLang, final boolean overwriteExisting, final Session session) throws Exception {
        final String baselinePath = sourcePath + "/jcr:content/metadata/baselines/" + baselineName;
        final String srcLang = this.translationUtils.getLanguage(sourcePath);
        final Node baselineNode = session.getNode(baselinePath);
        final Baseline bl = Baseline.getFromNode(baselineNode, session);
        final VersionManager vm = session.getWorkspace().getVersionManager();
        for (final BaselineRef blref : bl.directRefs) {
            final String targetVersion = MiscUtils.getTranslatedVersion(blref.coPath, destLang, blref.activeVersion, session);
            if (targetVersion.equals("missing") || targetVersion.equals("inProgress")) {
                TranslationReports.logger.error("No translated copy exists for version " + blref.activeVersion + " for path " + blref.coPath);
                throw new Exception("All required file versions don't have a translated copy.");
            }
            blref.coPath = blref.coPath.replaceFirst("/" + srcLang + "/", "/" + destLang + "/");
            final Version version = vm.getVersionHistory(blref.coPath).getVersion(targetVersion);
            blref.path = version.getFrozenNode().getPath();
            blref.date = String.valueOf(version.getCreated().getTime().getTime());
            blref.activeVersion = targetVersion;
            blref.selectedVersion = targetVersion;
            if (blref.parentMap != null) {
                for (int i = 0; i < blref.parentMap.size(); ++i) {
                    blref.parentMap.set(i, blref.parentMap.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
                }
            }
            for (int i = 0; i < blref.refs.size(); ++i) {
                blref.refs.set(i, blref.refs.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
            }
            for (int i = 0; i < blref.whereUsed.size(); ++i) {
                blref.whereUsed.set(i, blref.whereUsed.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
            }
        }
        for (final BaselineRef blref : bl.indirectRefs) {
            final String targetVersion = MiscUtils.getTranslatedVersion(blref.coPath, destLang, blref.activeVersion, session);
            if (targetVersion.equals("missing") || targetVersion.equals("inProgress")) {
                TranslationReports.logger.error("No translated copy exists for version " + blref.activeVersion + " for path " + blref.coPath);
                throw new Exception("All required file versions don't have a translated copy.");
            }
            blref.coPath = blref.coPath.replaceFirst("/" + srcLang + "/", "/" + destLang + "/");
            final Version version = vm.getVersionHistory(blref.coPath).getVersion(targetVersion);
            blref.path = version.getFrozenNode().getPath();
            blref.date = String.valueOf(version.getCreated().getTime().getTime());
            blref.activeVersion = targetVersion;
            blref.selectedVersion = targetVersion;
            if (blref.parentMap != null) {
                for (int i = 0; i < blref.parentMap.size(); ++i) {
                    blref.parentMap.set(i, blref.parentMap.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
                }
            }
            for (int i = 0; i < blref.refs.size(); ++i) {
                blref.refs.set(i, blref.refs.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
            }
            for (int i = 0; i < blref.whereUsed.size(); ++i) {
                blref.whereUsed.set(i, blref.whereUsed.get(i).replaceFirst("/" + srcLang + "/", "/" + destLang + "/"));
            }
        }
        final String destPath = sourcePath.replaceFirst("/" + srcLang + "/", "/" + destLang + "/");
        final Node existingNode = BaselineUtils.getBaselineNode(destPath, bl.baselineTitle, session);
        if (existingNode != null) {
            if (!overwriteExisting) {
                throw new Exception("Baseline already exists.");
            }
            existingNode.remove();
            session.save();
        }
        final String nameBaseline = BaselineUtils.createBaseline(destPath, bl.baselineTitle, session);
        final String destBaselinePath = destPath + "/jcr:content/metadata/baselines/" + nameBaseline;
        final Node destBaselineNode = session.getNode(destBaselinePath);
        bl.saveToNode(destBaselineNode, session);
    }
    
    @Activate
    protected void activate(final Map<String, Object> properties) {
        TranslationReports.SHOW_ALL_TRANSLATION = PropertiesUtil.toBoolean(properties.get("show.all.files"), false);
    }
    
    private HashSet<String> getAllLanguageCodes(final String rootFolder, final List<String> paths, final ResourceResolver resolver) throws Exception {
        final HashSet<String> languageCodes = new HashSet<String>();
        final ReferenceList referenceList = this.referenceAggregator.createReferenceList(resolver.getResource(rootFolder), new String[] { "assetLanguageCopy" });
        boolean flag = false;
        int lastIndex = rootFolder.lastIndexOf(47);
        String basePath = null;
        if (lastIndex >= 0) {
            basePath = rootFolder.substring(0, lastIndex);
        }
        for (final com.adobe.granite.references.Reference reference : referenceList) {
            if (reference.getTarget() != null) {
                if (reference.getTarget().getPath().equals(rootFolder)) {
                    continue;
                }
                if (lastIndex < 0) {
                    continue;
                }
                String languageRoot = LanguageUtil.getLanguageRoot(reference.getTarget().getPath());
                if (languageRoot == null) {
                    continue;
                }
                lastIndex = languageRoot.lastIndexOf(47);
                languageRoot = ((lastIndex >= 0) ? languageRoot.substring(0, lastIndex) : "");
                if (!languageRoot.equals(basePath)) {
                    continue;
                }
                languageCodes.add(this.translationUtils.getLanguage(reference.getTarget().getPath()));
                flag = true;
            }
        }
        if (!flag) {
            throw new Exception("Ensure that there is more than one language copy present.");
        }
        return languageCodes;
    }
    
    private Map<String, Object> addTopicToMap(final Ref ref) throws ParseException {
        final Map<String, Object> topic = new HashMap<String, Object>();
        topic.put("code", ref.code);
        topic.put("disp_code", LanguageUtil.getLocale(ref.code).getDisplayLanguage() + " ");
        topic.put("src_lang", StringUtils.isBlank(ref.srcLang) ? "" : (LanguageUtil.getLocale(ref.srcLang).getDisplayLanguage() + ""));
        topic.put("src_lang_code", ref.srcLang);
        topic.put("src_version", ref.srcVersion);
        topic.put("root", ref.languageRoot);
        topic.put("path", ref.path);
        topic.put("lastModified", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse(ref.lastModified)));
        topic.put("author", ref.author);
        topic.put("title", ref.title);
        topic.put("type", ref.type);
        return topic;
    }
    
    private List<String> getAllReferencePaths(final String source, final ResourceResolver resolver) throws Exception {
        final List<String> reference_paths = new ArrayList<String>();
        final HashSet<String> resources = new HashSet<String>();
        final Session session = (Session)resolver.adaptTo((Class)Session.class);
        final ProfileCache cache = new ProfileCache();
        final ValueFactory valFactory = session.getValueFactory();
        final LinkedList<Value> values = new LinkedList<Value>();
        try {
            final Value[] vals = this.getRefsFromPath(source, session, cache);
            values.addAll(Arrays.asList(vals));
        }
        catch (Exception e2) {
            throw new Exception("fmDependents property has not been set. Please open and save the ditamap in mapeditor without any edits.");
        }
        Value value = values.poll();
        while (value != null) {
            try {
                String path = this.getPathIfConref(value.getString(), source);
                path = PathUtils.getAbsolutePath(source, path);
                final Resource resource = resolver.getResource(path);
                if (resource == null) {
                    value = values.poll();
                    continue;
                }
                if (!TranslationReports.SHOW_ALL_TRANSLATION && path.endsWith(".ditamap")) {
                    resources.add(path);
                    value = values.poll();
                    continue;
                }
                resources.add(path);
                final Value[] fmDependants = this.getRefsFromPath(path, session, cache);
                for (int i = 0; i < fmDependants.length; ++i) {
                    final Value fmDependant = fmDependants[i];
                    final String fmDependantPath = this.getPathIfConref(fmDependant.getString(), path);
                    final String absPath = PathUtils.getAbsolutePath(path, fmDependantPath);
                    if (!resources.contains(absPath)) {
                        resources.add(absPath);
                        values.offer(valFactory.createValue(absPath));
                    }
                }
            }
            catch (Exception e) {
                TranslationReports.logger.error(e.getMessage());
            }
            value = values.poll();
        }
        reference_paths.addAll(resources);
        return reference_paths;
    }
    
    private Value[] getRefsFromPath(final String path, final Session session, final ProfileCache cache) throws Exception {
        final List<com.adobe.fmdita.postprocess.Reference> outrefs = (List<com.adobe.fmdita.postprocess.Reference>)ReferenceManagement.getOutRefs(path, session, cache);
        final List<Value> values = new ArrayList<Value>();
        final List<Value> conrefValues = new ArrayList<Value>();
        final List<Value> multimediaValues = new ArrayList<Value>();
        final HrefAdapter hrefAdapter = new HrefAdapter(path, session, ReferenceManagement.RefType.OUTREF, true);
        final ConrefAdapter conrefAdapter = new ConrefAdapter(path, session, ReferenceManagement.RefType.OUTREF, true);
        final DataAdapter dataAdapter = new DataAdapter(path, session, ReferenceManagement.RefType.OUTREF, true);
        hrefAdapter.adapt((List)outrefs, (List)values);
        conrefAdapter.adapt((List)outrefs, (List)conrefValues);
        dataAdapter.adapt((List)outrefs, (List)multimediaValues);
        values.addAll(conrefValues);
        values.addAll(multimediaValues);
        return values.toArray(new Value[0]);
    }
    
    private String getPathIfConref(String temp_val, final String filePath) {
        final int temp_val_index = temp_val.indexOf("#");
        if (temp_val_index != -1) {
            temp_val = temp_val.substring(0, temp_val_index);
        }
        if (StringUtils.isEmpty(temp_val)) {
            temp_val = filePath;
        }
        return temp_val;
    }
    
    private void getAssets(final String source, final String target, final List<String> statusList, final String type, final String lastModified, final Map<String, Object> report, final ResourceResolver resolver) throws Exception {
        if (this.translationUtils.getLanguageRoot(source) == null) {
            throw new Exception("Ensure that the ditamap is underneath a folder with a valid language code as its name.");
        }
        final List<String> languageCodes = new ArrayList<String>();
        List<String> reference_paths = new ArrayList<String>();
        final List<Object> topics = new ArrayList<Object>();
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        long out_of_sync = 0L;
        long missing_target = 0L;
        reference_paths = this.getAllReferencePaths(source, resolver);
        if (target.equals("ALL;")) {
            final HashSet<String> all_codes = this.getAllLanguageCodes(LanguageUtil.getLanguageRoot(source), reference_paths, resolver);
            languageCodes.addAll(all_codes);
        }
        else {
            final String[] codes = target.split(";");
            for (int i = 0; i < codes.length; ++i) {
                if (codes[i].length() != 0) {
                    languageCodes.add(codes[i]);
                }
            }
        }
        final List<Map<String, String>> languages = new ArrayList<Map<String, String>>();
        for (final String code : languageCodes) {
            final Map<String, String> obj = new HashMap<String, String>();
            obj.put("code", code);
            obj.put("disp_code", LanguageUtil.getLocale(code).getDisplayLanguage() + "");
            languages.add(obj);
        }
        report.put("languageCodes", languages);
        final Date d = StringUtils.isEmpty(lastModified) ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(lastModified);
        for (final String path : reference_paths) {
            try {
                final Ref ref = new Ref(path, this.repository, this.translationUtils.getLanguage(path), this.translationUtils.getLanguageRoot(path));
                if (ref.languageRoot == null) {
                    continue;
                }
                if (!type.equals("ALL") && !ref.type.equals(type)) {
                    continue;
                }
                final VersionManager vm = ((Session)resolver.adaptTo((Class)Session.class)).getWorkspace().getVersionManager();
                Version version = null;
                try {
                    version = vm.getBaseVersion(path);
                    
                    if ("jcr:rootVersion".equals(version.getName())) {
                        version = null;
                    }
                }
                catch (RepositoryException e) {
                    TranslationReports.logger.error("Error in getting base version of path {}", (Object)path, (Object)e.getMessage());
                }
                final Node versionNode = this.translationUtils.getVersionNode(path, (Session)resolver.adaptTo((Class)Session.class), "");
                final String versionLastModified = (NodeUtils.getNodeProperty(versionNode, "jcr:lastModified") != null) ? NodeUtils.getNodeProperty(versionNode, "jcr:lastModified").getString() : "";
                Date d2 = null;
                try {
                    d2 = df.parse(versionLastModified);
                }
                catch (ParseException e2) {
                    TranslationReports.logger.warn("Error in parsing lastmodified for path " + path, (Throwable)e2);
                }
                if (d != null && (d2 == null || d2.compareTo(d) < 0)) {
                    continue;
                }
                final Map<String, Object> topic = this.addTopicToMap(ref);
                final Boolean versionAvailable = version != null;
                topic.put("versionAvailable", versionAvailable);
                topic.put("version", ((boolean)versionAvailable) ? version.getName() : null);
                topic.put("versionLabels", ((boolean)versionAvailable) ? version.getContainingHistory().getVersionLabels(version) : new String[0]);
                final List<Map<String, Object>> cList = new ArrayList<Map<String, Object>>();
                for (final String languageCode : languageCodes) {
                    if (ref.code.equals(languageCode)) {
                        continue;
                    }
                    final Ref cRef = this.translationUtils.findLanguageCopy(path, languageCode, resolver);
                    Map<String, Object> cTopic;
                    if (cRef.path.equals("")) {
                        cTopic = new HashMap<String, Object>();
                        final String fakeLanguagePath = this.getFakeLanguagePath(ref.path, ref.code, languageCode);
                        cTopic.put("title", ref.title);
                        cTopic.put("type", ref.type);
                        cTopic.put("code", languageCode);
                        cTopic.put("disp_code", LanguageUtil.getLocale(languageCode).getDisplayLanguage() + " ");
                        cTopic.put("src_lang", "");
                        cTopic.put("src_lang_code", "");
                        cTopic.put("src_version", "");
                        cTopic.put("path", fakeLanguagePath);
                        cTopic.put("status", "Missing copy");
                        Version cVersion = null;
                        try {
                            cVersion = vm.getBaseVersion(fakeLanguagePath);
                            if ("jcr:rootVersion".equals(cVersion.getName())) {
                                cVersion = null;
                            }
                        }
                        catch (RepositoryException e3) {
                            TranslationReports.logger.error("Error in getting base version of path {}", (Object)fakeLanguagePath, (Object)e3.getMessage());
                        }
                        cTopic.put("version", (cVersion != null) ? cVersion.getName() : null);
                    }
                    else {
                        cTopic = this.addTopicToMap(cRef);
                        if (cRef.inProgress) {
                            cTopic.put("status", "In Progress");
                        }
                        else {
                            boolean isOutOfSync = StringUtils.isBlank(cRef.srcLang) || StringUtils.isBlank(cRef.srcVersion);
                            if (!isOutOfSync) {
                                try {
                                    final String currentSrcVersion = vm.getBaseVersion(this.getFakeLanguagePath(cRef.path, cRef.code, cRef.srcLang)).getName();
                                    isOutOfSync = !cRef.srcVersion.equals(currentSrcVersion);
                                }
                                catch (RepositoryException e4) {
                                    TranslationReports.logger.warn("Error in checking sync status based on source language version comparision for path " + cRef.path, (Throwable)e4);
                                }
                            }
                            cTopic.put("status", isOutOfSync ? "Out of Sync" : "In Sync");
                        }
                        Version cVersion2 = null;
                        try {
                            cVersion2 = vm.getBaseVersion(cRef.path);
                            if ("jcr:rootVersion".equals(cVersion2.getName())) {
                                cVersion2 = null;
                            }
                        }
                        catch (RepositoryException e4) {
                            TranslationReports.logger.error("Error in getting base version of path {}", (Object)cRef.path, (Object)e4.getMessage());
                        }
                        cTopic.put("version", (cVersion2 != null) ? cVersion2.getName() : null);
                    }
                    if (this.applySyncStatusFilter(statusList, cTopic.get("status").toString())) {
                        continue;
                    }
                    cList.add(cTopic);
                }
                topic.put("status", this.tStatus[0]);
                for (final Map<String, Object> cTopic2 : cList) {
                    final String cTopicStatus = cTopic2.get("status").toString();
                    if ("Missing copy".equals(cTopicStatus)) {
                        ++missing_target;
                    }
                    if ("Out of Sync".equals(cTopicStatus)) {
                        ++out_of_sync;
                    }
                    this.updateTopicStatus(topic, cTopicStatus);
                }
                topic.put("copies", cList);
                if (cList.size() == 0) {
                    continue;
                }
                topics.add(topic);
            }
            catch (Exception e5) {
                TranslationReports.logger.error("Error in extracting translation-related info about path {}", (Object)path, (Object)e5.getMessage());
            }
        }
        report.putAll(this.addTopicToMap(new Ref(source, this.repository, this.translationUtils.getLanguage(source), this.translationUtils.getLanguageRoot(source))));
        report.put("topics", topics);
        report.put("outofsync", out_of_sync);
        report.put("missingtarget", missing_target);
    }
    
    private void updateTopicStatus(final Map<String, Object> topic, final String status) {
        if ("In Progress".equals(status)) {
            topic.put("status", "In Progress");
            return;
        }
        final String topicStatus = topic.get("status").toString();
        if ("In Progress".equals(topicStatus) || "Out of Date".equals(topicStatus)) {
            return;
        }
        topic.put("status", "In Sync".equals(status) ? "Up to Date" : "Out of Date");
    }
    
    private boolean applySyncStatusFilter(final List<String> statusList, final String topicStatus) {
        final String topicStatusCode = this.statusMap.get(topicStatus);
        return !statusList.isEmpty() && !statusList.contains(topicStatusCode);
    }
    
    private void processSyncJsons(final SyncJsonWrapper syncJsonWrapper, final ResourceResolver resolver,boolean fromCollection) {
        for (final SyncJson syncJson : syncJsonWrapper.syncJsons) {
            final String source = syncJson.source;
            for (final String target : syncJson.destinations) {
                final String dest = this.translationUtils.getLanguage(target);
                if (resolver.getResource(target) == null) {
                    this.createLanguageCopy(source, dest + ";", resolver, true,fromCollection);
                }
            }
        }
    }
    
    private String getFakeLanguagePath(final String path, final String code, final String languageCode) {
        return path.replaceFirst("/" + code + "/", "/" + languageCode + "/");
    }
    
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServerException, IOException {
        final ResourceResolver resolver = request.getResourceResolver();
        try {
            String payload = URLDecoder.decode(request.getParameter("payload"), "UTF-8");
            final String operation = request.getParameter("operation");
            if (operation.equals("getlanguagecopies")) {
                final Gson gson = new Gson();
                final ReferenceList referenceList = (ReferenceList)resolver.getResource(payload).adaptTo((Class)ReferenceList.class);
                final List<com.adobe.granite.references.Reference> filteredReferences = (List<com.adobe.granite.references.Reference>)referenceList.subList(new String[] { "assetLanguageCopy" });
                final List<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
                for (final com.adobe.granite.references.Reference reference : filteredReferences) {
                    final String linkedResource = reference.getTarget().getPath();
                    final String language = this.translationUtils.getLanguage(linkedResource);
                    final HashMap<String, String> map = new HashMap<String, String>();
                    map.put("path", linkedResource);
                    map.put("language", language);
                    result.add(map);
                }
                response.getWriter().write(gson.toJson((Object)result));
            }
            else if (operation.equals("getrootlanguages")) {
                final List<HashMap<String, String>> res = new ArrayList<HashMap<String, String>>();
                final String languageRoot = LanguageUtil.getLanguageRoot(payload);
                if (languageRoot == null) {
                    throw new Exception("Ensure that the ditamap is underneath a folder with a valid language code as its name.");
                }
                final HashSet<String> resset = this.getAllLanguageCodes(languageRoot, null, resolver);
                for (final String code : resset) {
                    final HashMap<String, String> m = new HashMap<String, String>();
                    m.put("code", code);
                    m.put("displayCode", LanguageUtil.getLocale(code).getDisplayLanguage());
                    res.add(m);
                }
                final Gson gson2 = new Gson();
                final String metadatajson = gson2.toJson((Object)res);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print(metadatajson);
                response.getWriter().flush();
                response.getWriter().close();
            }
            else if (operation.equals("getditamap")) {
                final String source = request.getParameter("source");
                if (source != null) {
                    payload = this.translationUtils.findLanguageCopy(payload, request.getParameter("source"), resolver).path;
                }
                final String target = request.getParameter("target");
                final String status = request.getParameter("status");
                String[] statusArr;
                if (StringUtils.isBlank(status)) {
                    statusArr = new String[0];
                }
                else {
                    statusArr = status.split(";");
                }
                String type = request.getParameter("type");
                if (type == null) {
                    type = "";
                }
                String lastModified = request.getParameter("lastmodified");
                if (lastModified == null) {
                    lastModified = "";
                }
                final Map<String, Object> report = new HashMap<String, Object>();
                this.getAssets(payload, target, Arrays.asList(statusArr), type, lastModified, report, resolver);
                final Gson gson3 = new Gson();
                final String metadatajson2 = gson3.toJson((Object)report);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print(metadatajson2);
                response.getWriter().flush();
                response.getWriter().close();
            }
            else if (operation.equals("fixLinks")) {
                final List<String> reference_paths = new ArrayList<String>();
                final MergeXML merger = new MergeXML(true);
                reference_paths.add(payload);
                reference_paths.addAll(this.getAllReferencePaths(payload, resolver));
                for (final String path : reference_paths) {
                    if (XmlGlobals.DITA_FILE_EXTENSIONS.contains(FilenameUtils.getExtension(path))) {
                        try {
                            merger.processXML(path, (Session)resolver.adaptTo((Class)Session.class));
                        }
                        catch (Exception e) {
                            TranslationReports.logger.info(e.getMessage() + ". Skipping .." + path);
                        }
                    }
                }
            }
        }
        catch (Exception e2) {
            response.setStatus(432);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().print(e2.getMessage());
            TranslationReports.logger.error(e2.getMessage());
            response.getWriter().flush();
            response.getWriter().close();
        }
    }
    
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServerException, IOException {
        final ResourceResolver resolver = request.getResourceResolver();
        String[] targetLocales = new String[] {};
		String[] assetPaths = new String[] {};
		boolean fromCollection = false;
		String projectTitle = null;		
		Map<String, String> valueMap = new HashMap<String, String>();
		String projectPath = null;
		String fileName = null;
		String collectionPath=null;     
		String projectType = null;
        try {
            final String operation = request.getParameter("operation");
            if (operation.equals("synctranslate")) {
                final String temp = request.getParameter("json");
                logger.debug("JSON:  {}", temp);
                final String payload = request.getParameter("payload");
                logger.debug("payload:  {}", payload);
                projectType = request.getParameter("projectType");
                logger.debug("projectType:  {}", projectType);
                projectPath = request.getParameter("projectPath");
                logger.debug("projectPath:  {}", projectPath);
                final boolean smartAsset = Boolean.valueOf(request.getParameter("smartAsset"));
                final ProjectManager pm = (ProjectManager)request.getResourceResolver().adaptTo((Class)ProjectManager.class);
                final Gson gson = new Gson();
                //collection changes - start
                fromCollection = null != request.getParameter("fromCollection")
						? (Boolean.parseBoolean(request.getParameter("fromCollection")))
						: false;
				logger.debug("request is from collection? : {}", fromCollection);

				if (fromCollection) {
					Session session = (Session) request.getResourceResolver().adaptTo(Session.class);
					logger.debug("In collection translation model");
					projectTitle = request.getParameter("projectTitle");
					logger.debug("projectTitle : {}", projectTitle);
					RequestParameter chpCollData = request.getRequestParameter("reviewparams");
					JsonElement jElement = new JsonParser().parse(chpCollData.toString());
					JsonObject localeObj = jElement.getAsJsonObject();
					logger.debug("localeObj : {}", localeObj.toString());
					targetLocales = requestJsonObject(localeObj, "targetLocales");
					logger.debug("targetLocales : {}", targetLocales);
					assetPaths = requestJsonObject(localeObj, "assetPaths");
					logger.debug("assetPaths : {}", assetPaths);
					projectPath = request.getParameter("projectPath");
					collectionPath = request.getParameter("collectionPath");
					logger.debug("Collection Path  : {}", collectionPath);
					// iterating on assets from UI
					for (int j = 0; j < assetPaths.length; j++) {
						logger.debug("assetPaths :{}", assetPaths[j]);
						Node payloadNode = session.getNode(assetPaths[j]);
						// getting dependencies and forming json
						List<Node> dependencies = MapUtilities.getAllDependencies(payloadNode);
						logger.debug("dependencies size :{}", dependencies.size());
						JSONObject collTempString = populateReferenceJson(targetLocales, dependencies, session);
						collTempString.put("projectTitle", projectTitle);
						collTempString.put("projectPath", projectPath);
						logger.debug("Single json is :{}", collTempString.toString());
						SyncJsonWrapper syncJsonWrapper = (SyncJsonWrapper) gson.fromJson(collTempString.toString(),
								SyncJsonWrapper.class);
						if (j == 0) {
							projectType = "add_new";
						} else
							projectType = "add_existing";
						logger.debug("===>syncTranslate for {}<===", assetPaths[j]);
						//syncTranslateCopies(assetPaths[j], syncJsonWrapper, projectType, pm, smartAsset, resolver);
						this.syncTranslateExecutor.addTranslationSyncJob(assetPaths[j], syncJsonWrapper, projectType, pm, smartAsset, resolver,fromCollection,collectionPath);
					}

					// Initiate Email Notification On success of bulk translation
					Session crxSession = resolver.adaptTo(Session.class);
					logger.debug("crxSession- getUserId() : {}", crxSession.getUserID());
					// get author link to keep full path in mail body
					Externalizer externalizer = null;
					String authorLink = null;
					externalizer = resolver.adaptTo(Externalizer.class);
					authorLink = externalizer.authorLink(resolver, "/");
					logger.debug("resolver : {}", resolver);
					logger.debug("pageManagerFactory : {}", pageManagerFactory);
					logger.debug("emailNotificationService : {}", emailNotificationService);
					logger.debug("messageGatewayService : {}", messageGatewayService);
					logger.debug("template : {}", XMLRenditionConstants.BULK_TRANSLATION_INITIATED_SUCCESS_TEMPLATE);
					valueMap.put("requestedUser", crxSession.getUserID());
					collectionPath = request.getParameter("collectionPath");
					logger.debug("Collection Path  : {}", collectionPath);
					valueMap.put("file.name", collectionPath);
					String projectURL = authorLink + "libs/cq/core/content/projects.html" + projectPath;
					logger.debug("projectURL: {}", projectURL);
					valueMap.put("project.url", projectURL);
					XMLEmailNotificationUtility.sendRenditionSuccessEmail(resolver, emailNotificationService,
							messageGatewayService, XMLRenditionConstants.BULK_TRANSLATION_INITIATED_SUCCESS_TEMPLATE,
							"WEM Notification - Bulk Translation Initiation Success", collectionPath, valueMap,
							repository);
					response.setStatus(200);
					response.getWriter().print("Sent for translation");
					response.getWriter().flush();
					response.getWriter().close();

				}//collection changes -end
				else {
				//Single asset model, OOB changes, did not customize any thing                
                final SyncJsonWrapper syncJsonWrapper = (SyncJsonWrapper)gson.fromJson(temp, (Class)SyncJsonWrapper.class);
                final Boolean success = this.syncTranslateExecutor.addTranslationSyncJob(payload, syncJsonWrapper, projectType, pm, smartAsset, resolver,fromCollection,collectionPath);
                if (success) {
                    response.setStatus(200);
                    response.getWriter().print("The translation request has been submitted. You will receive a notification when the request is completed");
                }
                else {
                    response.setStatus(500);
                    response.getWriter().print("There has been some error while sending for translation");
                }
                response.getWriter().flush();
                response.getWriter().close();
            	// Initiate Email to the user
				Session crxSession = resolver.adaptTo(Session.class);
				logger.debug("crxSession- getUserId() : {}", crxSession.getUserID());

				// get author link to keep full path in mail body
				Externalizer externalizer = null;
				String authorLink = null;
				externalizer = resolver.adaptTo(Externalizer.class);
				authorLink = externalizer.authorLink(resolver, "/");
				logger.debug("resolver : {}", resolver);
				logger.debug("pageManagerFactory : {}", pageManagerFactory);

				logger.debug("emailNotificationService : {}", emailNotificationService);
				logger.debug("messageGatewayService : {}", messageGatewayService);
				logger.debug("template : {}", XMLRenditionConstants.TRANSLATION_INITIATED_SUCCESS_TEMPLATE);

				valueMap.put("requestedUser", crxSession.getUserID());

				if (null != payload) {
					fileName = payload.substring(payload.lastIndexOf("/") + 1, payload.length());
				}
				logger.debug("fileName : {}", fileName);
				valueMap.put("file.name", fileName);
				// http://localhost:4502/libs/cq/core/content/projects.html
				String projectURL = authorLink + "libs/cq/core/content/projects.html" + projectPath;
				logger.debug("projectURL: {}", projectURL);
				valueMap.put("project.url", projectURL);
				XMLEmailNotificationUtility.sendRenditionSuccessEmail(resolver, emailNotificationService,
						messageGatewayService, XMLRenditionConstants.TRANSLATION_INITIATED_SUCCESS_TEMPLATE,
						"WEM Notification - Translation Initiation Success", payload, valueMap, repository);
				}
            }

            else if (operation.equals("sync")) {
                final String payload2 = request.getParameter("payload");
                final String temp2 = request.getParameter("json");
                final Gson gson2 = new Gson();
                final SyncJsonWrapper syncJsonWrapper2 = (SyncJsonWrapper)gson2.fromJson(temp2, (Class)SyncJsonWrapper.class);
                this.processSyncJsons(syncJsonWrapper2, resolver,fromCollection);
                response.setStatus(200);
                response.getWriter().print("Language Copies created successfully.");
                response.getWriter().flush();
                response.getWriter().close();
            }
            else if (operation.equals("accepttranslation")) {
            	logger.debug(" ******** acceptTranslation call from TranslationReport ******");
                final String[] paths = request.getParameter("paths").split("\\|");
                final String[] jobs = request.getParameter("jobs").split("\\|");
                final Session session = (Session)request.getResourceResolver().adaptTo((Class)Session.class);
                this.translationUtils.acceptTranslation(paths, jobs, resolver, session,fromCollection);
            }
            else if (operation.equals("rejecttranslation")) {
                final String[] paths = request.getParameter("paths").split("\\|");
                final String[] jobs = request.getParameter("jobs").split("\\|");
                this.rejectTranslation(paths, jobs, resolver);
            }
            else if (operation.equals("translatebaseline")) {
                final Session session2 = (Session)request.getResourceResolver().adaptTo((Class)Session.class);
                final String sourcePath = request.getParameter("ditamap");
                final String baselineName = request.getParameter("baseline");
                final String[] destLangs = request.getParameter("destLang").split(";");
                boolean overwriteExisting = false;
                if (request.getParameterMap().containsKey("overwriteExisting")) {
                    overwriteExisting = request.getParameter("overwriteExisting").equals("true");
                }
                final StringBuilder logBuffer = new StringBuilder("");
                for (final String destLang : destLangs) {
                    try {
                        this.translateBaseline(sourcePath, baselineName, destLang, overwriteExisting, session2);
                        logBuffer.append("Baseline created successfully for language: " + destLang + "<br/>");
                    }
                    catch (Exception e) {
                        TranslationReports.logger.error(e.toString());
                        logBuffer.append("Baseline creation failed for language: " + destLang + ", due to: " + e.getMessage() + "<br/>");
                    }
                }
                response.getWriter().print(logBuffer.toString());
                response.getWriter().flush();
                response.getWriter().close();
                session2.save();
                session2.logout();
            }
            else if (operation.equals("forcesync")) {
                final String files = request.getParameter("files");
                final JSONArray filesArr = new JSONArray(files);
                this.forceSync(filesArr);
            }
            else if (operation.equalsIgnoreCase("forcesyncbulk")) {
                final String rootFolder = (request.getParameter("root") == null) ? "/content/dam" : request.getParameter("root");
                final String srcLang = (request.getParameter("srclang") == null) ? "en" : request.getParameter("srclang");
                final String excludePathsParam = request.getParameter("excludepaths");
                final String[] excludePaths = StringUtils.isBlank(excludePathsParam) ? new String[0] : excludePathsParam.split("\\|");
                Session session3 = null;
                final PrintWriter writer = response.getWriter();
                try {
                    session3 = (Session)resolver.adaptTo((Class)Session.class);
                    writer.println("Force-sync starting with root folder \"" + rootFolder + "\" and source language \"" + srcLang + "\"...");
                    this.forceSyncBulk(session3.getNode(rootFolder), srcLang, Arrays.asList(excludePaths), session3, writer);
                    response.setStatus(200);
                    writer.println("Force-sync completed.");
                    writer.flush();
                    writer.close();
                }
                catch (Exception e2) {
                    TranslationReports.logger.error("Error in bulk force-sync: ", (Throwable)e2);
                    response.setStatus(500);
                    writer.println("Error occured in bulk force-sync.");
                    writer.flush();
                    writer.close();
                }
                finally {
                    if (session3 != null) {
                        session3.logout();
                    }
                }
            }
        }
        catch (Exception e3) {
            response.setStatus(432);
            response.getWriter().print(e3.toString());
            TranslationReports.logger.error("Unable to execute doPost in translation due to ", (Throwable)e3);
            response.getWriter().flush();
            response.getWriter().close();
        }
    }
    
    private void rejectTranslation(final String[] paths, final String[] jobs, final ResourceResolver resolver) {
        try {
            final Session session = (Session)resolver.adaptTo((Class)Session.class);
            for (int i = 0; i < paths.length; ++i) {
                String oldPath = "";
                final String path = paths[i];
                final String job = jobs[i];
                oldPath = session.getNode(path + "/" + "jcr:content").getProperty("fmTarget").getString();
                session.getNode(job).setProperty("translationStatus", "Rejected");
                session.getNode(job).setProperty("sourcePath", oldPath);
                try {
                    session.getNode(oldPath).getNode("jcr:content").getNode("metadata").getProperty("inProgress").remove();
                    final String srcLang = session.getNode(path).getNode("jcr:content/metadata").getProperty("srcLang").getString();
                    final String destPath = oldPath;
                    final String destLang = this.translationUtils.getLanguage(destPath);
                    final String srcPath = destPath.replaceFirst("/" + destLang + "/", "/" + srcLang + "/");
                    final String srcVersionName = session.getNode(path).getNode("jcr:content/metadata").getProperty("srcVersion").getString();
                    this.translationUtils.setTranslatedVersion(srcPath, srcVersionName, destLang, "reject", session);
                }
                catch (Exception e) {
                    TranslationReports.logger.error("Error in removing inProgress property.", (Throwable)e);
                }
                session.getNode(path).remove();
                session.save();
            }
        }
        catch (Exception e2) {
            TranslationReports.logger.error(e2.getMessage());
        }
    }
    
    private void createLanguageCopy(final String source, final String translateLanguages, final ResourceResolver resolver, final boolean saveSession,boolean fromCollection) {
        try {
            final Session session = (Session)resolver.adaptTo((Class)Session.class);
            DamLanguageUtil.createLanguageCopy(resolver, this.pageManagerFactory, source, translateLanguages.split(";"));
            if (source.endsWith(".ditamap")) {
                for (final String lang : translateLanguages.split(";")) {
                    if (lang.trim().length() != 0) {
                        final String targetPath = this.translationUtils.findLanguageCopy(source, lang,false,fromCollection);
                        this.translationUtils.removeBaselines(targetPath, session);
                    }
                }
            }
            if (saveSession) {
                ((Session)resolver.adaptTo((Class)Session.class)).save();
            }
        }
        catch (Exception e) {
            TranslationReports.logger.error(e.getMessage());
        }
    }
    
    private void forceSync(final JSONArray filesArr) throws Exception {
        Session session = null;
        try {
            session = this.repository.loginService("fmdita-serviceuser", (String)null);
            for (int i = 0; i < filesArr.length(); ++i) {
                try {
                    final JSONObject item = filesArr.getJSONObject(i);
                    final String srcFile = item.getString("srcPath");
                    final String copiedFile = item.getString("copyPath");
                    this.forceSync(srcFile, copiedFile, session);
                }
                catch (JSONException | RepositoryException ex2) {
                    final Exception ex;
                    final Exception e = ex2;
                    TranslationReports.logger.error("Error in force-sync:", (Throwable)e);
                }
            }
            session.save();
        }
        finally {
            if (session != null) {
                session.logout();
            }
        }
    }
    
    private void forceSync(final String srcPath, final String copyPath, final Session session) throws RepositoryException {
        if (srcPath == null || copyPath == null) {
            return;
        }
        final Ref ref = new Ref(srcPath, this.repository, this.translationUtils.getLanguage(srcPath), this.translationUtils.getLanguageRoot(srcPath));
        final Ref cRef = new Ref(copyPath, this.repository, this.translationUtils.getLanguage(copyPath), this.translationUtils.getLanguageRoot(copyPath));
        final Node metadataNode = session.getNode(copyPath + "/jcr:content/metadata");
        final VersionManager vm = session.getWorkspace().getVersionManager();
        String srcVersion;
        if (StringUtils.isBlank(cRef.srcLang)) {
            srcVersion = vm.getBaseVersion(srcPath).getName();
            metadataNode.setProperty("srcLang", ref.code);
        }
        else {
            srcVersion = vm.getBaseVersion(this.getFakeLanguagePath(cRef.path, cRef.code, cRef.srcLang)).getName();
        }
        metadataNode.setProperty("srcVersion", srcVersion);
    }
    
    private void forceSyncBulk(final Node root, final String srcLang, final List<String> excludePaths, final Session session, final PrintWriter writer) throws RepositoryException {
        final String rootPath = root.getPath();
        try {
            if (excludePaths.contains(rootPath)) {
                if (writer != null) {
                    writer.println("Ignoring path: " + rootPath);
                }
                return;
            }
            if ("dam:Asset".equals(root.getProperty("jcr:primaryType").getString())) {
                final String copyPath = rootPath;
                final String languageCode = this.translationUtils.getLanguage(copyPath);
                if (languageCode != null && !languageCode.equals(srcLang)) {
                    final String srcPath = this.getFakeLanguagePath(copyPath, languageCode, srcLang);
                    this.forceSync(srcPath, copyPath, session);
                    session.save();
                    if (writer != null) {
                        writer.println("Force sync successfully done at: " + copyPath);
                    }
                }
                else if (writer != null) {
                    writer.println("Not a translated file: " + copyPath);
                }
            }
            else {
                final NodeIterator nodes = root.getNodes();
                while (nodes.hasNext()) {
                    this.forceSyncBulk(nodes.nextNode(), srcLang, excludePaths, session, writer);
                }
            }
        }
        catch (RepositoryException e) {
            TranslationReports.logger.error("Error in force sync at node path " + rootPath, (Throwable)e);
            if (writer != null) {
                writer.println("Error in force sync at: " + rootPath);
            }
        }
    }
    
    private void createVersion(final String path, final ResourceResolver resolver) throws Exception {
        final Asset asset = (Asset)resolver.getResource(path).adaptTo((Class)Asset.class);
        asset.createRevision("", "");
    }
    
    static {
        TranslationReports.SHOW_ALL_TRANSLATION = false;
        logger = LoggerFactory.getLogger((Class)TranslationReports.class);
    }
    
    protected void bindPageManagerFactory(final PageManagerFactory pageManagerFactory) {
        this.pageManagerFactory = pageManagerFactory;
    }
    
    protected void unbindPageManagerFactory(final PageManagerFactory pageManagerFactory) {
        if (this.pageManagerFactory == pageManagerFactory) {
            this.pageManagerFactory = null;
        }
    }
    
    protected void bindTranslationUtils(final CustomTranslationUtils translationUtils) {
        this.translationUtils = translationUtils;
    }
    
    protected void unbindTranslationUtils(final CustomTranslationUtils translationUtils) {
        if (this.translationUtils == translationUtils) {
            this.translationUtils = null;
        }
    }
    
    protected void bindSyncTranslateExecutor(final CustomSyncTranslateExecutor syncTranslateExecutor) {
        this.syncTranslateExecutor = syncTranslateExecutor;
    }
    
    protected void unbindSyncTranslateExecutor(final CustomSyncTranslateExecutor syncTranslateExecutor) {
        if (this.syncTranslateExecutor == syncTranslateExecutor) {
            this.syncTranslateExecutor = null;
        }
    }
    
    protected void bindRepository(final ProxySlingRepository repository) {
        this.repository = repository;
    }
    
    protected void unbindRepository(final SlingRepository slingRepository) {
        if (this.repository == slingRepository) {
            this.repository = null;
        }
    }
    
    protected void bindWfService(final WorkflowService wfService) {
        this.wfService = wfService;
    }
    
    protected void unbindWfService(final WorkflowService workflowService) {
        if (this.wfService == workflowService) {
            this.wfService = null;
        }
    }
    
    protected void bindReferenceAggregator(final ReferenceAggregator referenceAggregator) {
        this.referenceAggregator = referenceAggregator;
    }
    
    protected void unbindReferenceAggregator(final ReferenceAggregator referenceAggregator) {
        if (this.referenceAggregator == referenceAggregator) {
            this.referenceAggregator = null;
        }
    }
    
    public String[] requestJsonObject(JsonObject obj, String searchString) {
    	logger.debug("requestJsonObject: searchString: {}", searchString);
		List<String> tempArray = new ArrayList<String>();
		String[] tempStrArray = new String[] {};
		if (obj.has(searchString)) {
			JsonArray locales = obj.get(searchString).getAsJsonArray();
			for (int i = 0; i < locales.size(); i++) {
				tempArray.add(locales.get(i).getAsString());
			}
			tempStrArray = tempArray.toArray(new String[tempArray.size()]);
		}
		return tempStrArray;
	}

	public JSONObject populateReferenceJson(String[] targetLocales, List<Node> dependencies, Session session)
			throws JSONException, RepositoryException {
		List<JSONObject> syncJsonJSONObject = new ArrayList<JSONObject>();
		String[] destinations = new String[] {};
		final VersionManager vm = session.getWorkspace().getVersionManager();
		logger.debug("populateReferenceJson(): VersionManager: {}", vm);
		// JSONArray referencesJarray = new JSONArray();
		JSONObject jobj = new JSONObject();

		for (int i = 0; i < dependencies.size(); i++) {
			List<String> destinationList = new ArrayList<String>();
			String tempDitaPath = dependencies.get(i).getPath();
			String locale = null;
		 if(!tempDitaPath.contains("/td/i/")){
			if (tempDitaPath.contains("/td-xml/")) {
				locale = StringUtils.substringBefore(StringUtils.substringAfter(tempDitaPath, "/td-xml/"), "/");
			} else if (tempDitaPath.contains("/td/i/")) {
				// content/dam/en/us/td/i
				locale = StringUtils.substringBefore(StringUtils.substringAfter(tempDitaPath, "/dam/"), "/td/");
			}
			for (String replaceLocale : targetLocales) {
				destinationList.add(StringUtils.replace(tempDitaPath, locale, replaceLocale));
			}
			destinations = destinationList.toArray(new String[destinationList.size()]);
			JSONObject singleReferenceJSONObject = new JSONObject();
			singleReferenceJSONObject.put("source", tempDitaPath);
			singleReferenceJSONObject.put("destinations", destinations);
			String currentSrcVersion = vm.getBaseVersion(tempDitaPath).getName();
			logger.debug("populateReferenceJson(): currentSrcVersion: {}", currentSrcVersion);
			//singleReferenceJSONObject.put("version", "1.0");
			singleReferenceJSONObject.put("version", currentSrcVersion);
			syncJsonJSONObject.add(singleReferenceJSONObject);
			logger.debug("======= JSON Object ==========: {}",singleReferenceJSONObject.toString());
			
		}
		}

		JSONArray syncJsons = new JSONArray(syncJsonJSONObject);
		JSONArray ditaMaps = new JSONArray();
		jobj.put("syncJsons", syncJsons);
		jobj.put("ditaMaps", ditaMaps);

		return jobj;
	}

}