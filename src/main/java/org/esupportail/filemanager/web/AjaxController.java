/**
 * Licensed to EsupPortail under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * EsupPortail licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.esupportail.filemanager.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.esupportail.filemanager.beans.*;
import org.esupportail.filemanager.services.IServersAccessService;
import org.esupportail.filemanager.services.ResourceUtils;
import org.esupportail.filemanager.services.ResourceUtils.Type;
import org.esupportail.filemanager.utils.PathEncodingUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Controller
@Scope("request")
@RequestMapping
public class AjaxController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AjaxController.class);

    @Autowired
    private MessageSource messageSource;

    @Autowired
    protected IServersAccessService serverAccess;

    @Autowired
    protected BasketSession basketSession;

    @Autowired
    protected ApplicationContext context;

    @Autowired(required=false)
    @Qualifier("useDoubleClickModeServlet")
    protected Boolean useDoubleClick = true;

    @Autowired(required=false)
    @Qualifier("useCursorWaitDialogModeServlet")
    protected Boolean useCursorWaitDialog = false;

    @Autowired(required=false)
    @Qualifier("showHiddenFilesModeServlet")
    protected Boolean showHiddenFilesModeServlet = false;

    @Autowired(required=false)
    @Qualifier("uploadActionOnExistingFileNameModeServlet")
    protected UploadActionType uploadActionOnExistingFileNameServlet = UploadActionType.OVERRIDE;


    //GP Added in order to detect file type (image / sound / etc)
    @Autowired
    protected ResourceUtils resourceUtils;

    @Autowired
    protected PathEncodingUtils pathEncodingUtils;

    /**
     * Data for the browser area.
     * @param dir
     * @param request
     * @param response
     * @return
     */
    @PostMapping(value="/htmlFileTree")
    public ModelAndView fileTree(@RequestParam String dir, @RequestParam(required=false) String sortField, HttpServletRequest request, HttpServletResponse response) {
        log.debug("Requesting htmlFileTree");
        dir = pathEncodingUtils.decodeDir(dir);
        log.debug("Requesting htmlFileTree on dir {}", dir);
        ModelMap model = new ModelMap();
        if(this.serverAccess.formAuthenticationRequired(dir)) {
            model = new ModelMap("currentDir", pathEncodingUtils.encodeDir(dir));
            model.put("username", this.serverAccess.getUserPassword(dir).getUsername());
            model.put("password", this.serverAccess.getUserPassword(dir).getPassword());
            return new ModelAndView("authenticationForm", model);
        }

        JsTreeFile resource = this.serverAccess.get(dir, false, false);
        pathEncodingUtils.encodeDir(resource);
        model.put("resource", resource);
        List<JsTreeFile> files = this.serverAccess.getChildren(dir);

        Comparator<JsTreeFile> comparator = JsTreeFile.comparators.get(sortField);
        if(comparator != null) {
            Collections.sort(files, comparator);
        } else {
            Collections.sort(files);
        }

        pathEncodingUtils.encodeDir(files);
        model.put("files", files);
        LinkedHashMap parentsEncPathes = pathEncodingUtils.getParentsEncPathes(resource);
        model.put("parentsEncPathes", parentsEncPathes);

        FormCommand command = new FormCommand();
        model.put("command", command);

        model.put("datePattern", context.getMessage("datePattern", null, request.getLocale()));
        return new ModelAndView("fileTree", model);
    }


    /**
     * Data for the left tree area
     * @param dir
     * @param request
     * @return
     */
    @PostMapping(value="/fileChildren")
    @ResponseBody
    public List<JsTreeFile> fileChildren(Authentication auth, @RequestParam String dir, @RequestParam(required=false) String hierarchy, HttpServletRequest request) {
        log.info("User authenticated as {}", auth.getName());
        log.debug("Requesting fileChildren");
        dir = pathEncodingUtils.decodeDir(dir);
        log.debug("Requesting fileChildren decoded dir is {}", dir);
        List<JsTreeFile> files;
        if(this.serverAccess.formAuthenticationRequired(dir) && StringUtils.isEmpty(this.serverAccess.getUserPassword(dir).getPassword())) {
            String driveDir = JsTreeFile.ROOT_DRIVE
                    .concat(this.serverAccess.getDriveCategory(dir));

            // we can't get children of (sub)children of a drive because authentication is required
            // -> we return empty list
            if("all".equals(hierarchy)) {
                files =  this.serverAccess.getJsTreeFileRoots(driveDir);
            } else if(dir.length() > driveDir.length()) {
                files = new ArrayList<JsTreeFile>();
            } else {
                files = this.serverAccess.getFolderChildren(driveDir);
            }
        } else {
            if(dir == null || dir.length() == 0 || dir.equals(JsTreeFile.ROOT_DRIVE) ) {
                files = this.serverAccess.getJsTreeFileRoots();
            } else if("all".equals(hierarchy)) {
                files =  this.serverAccess.getJsTreeFileRoots(dir);
            } else {
                files = this.serverAccess.getFolderChildren(dir);
            }
        }
        pathEncodingUtils.encodeDir(files);
        return files;
    }


    @PostMapping(value="/removeFiles")
    @ResponseBody
    public Map removeFiles(FormCommand command, HttpServletRequest request) {
        log.debug("Requesting removeFiles");
        Locale locale = request.getLocale();
        long allOk = 1;
        String msg = context.getMessage("ajax.remove.ok", null, locale);
        Map jsonMsg = new HashMap();
        for(String dir: pathEncodingUtils.decodeDirs(command.getDirs())) {
            if(!this.serverAccess.remove(dir)) {
                msg = context.getMessage("ajax.remove.failed", null, locale);
                allOk = 0;
            }
        }
        jsonMsg.put("status", allOk);
        jsonMsg.put("msg", msg);

        return jsonMsg;
    }

    @PostMapping(value="/createFile")
    public ModelAndView createFile(String parentDir, String title, String type, HttpServletRequest request, HttpServletResponse response) {
        log.debug("Requesting createFile");
        String parentDirDecoded = pathEncodingUtils.decodeDir(parentDir);
        String fileDir = this.serverAccess.createFile(parentDirDecoded, title, type);
        if(fileDir != null) {
            return this.fileTree(parentDir, null, request, response);
        }

        //Added for GIP Recia : Error handling
        //Usually a duplicate name problem.  Tell the ajax handler that
        //there is a problem and send the translated error message
        Locale locale = request.getLocale();
        ModelMap modelMap = new ModelMap();
        modelMap.put("errorText", context.getMessage("ajax.fileOrFolderCreate.failed", null, locale));
        return new ModelAndView("ajax_error", modelMap);
    }

    @PostMapping(value="/renameFile")
    public ModelAndView renameFile(String parentDir, String dir, String title, HttpServletRequest request, HttpServletResponse response) {
        log.debug("Requesting renameFile");
        parentDir = pathEncodingUtils.decodeDir(parentDir);
        dir = pathEncodingUtils.decodeDir(dir);
        if(this.serverAccess.renameFile(dir, title)) {
            return this.fileTree(pathEncodingUtils.encodeDir(parentDir), null, request, response);
        }

        //Usually means file does not exist
        Locale locale = request.getLocale();
        ModelMap modelMap = new ModelMap();
        modelMap.put("errorText", context.getMessage("ajax.rename.failed", null, locale));
        return new ModelAndView("ajax_error", modelMap);
    }

    @GetMapping(value="/prepareCopyFiles")
    @ResponseBody
    public Map prepareCopyFiles(FormCommand command, HttpServletRequest request) {
        log.debug("Requesting prepareCopyFiles");
        Locale locale = request.getLocale();
        basketSession.setDirsToCopy(pathEncodingUtils.decodeDirs(command.getDirs()));
        basketSession.setGoal("copy");
        Map jsonMsg = new HashMap();
        jsonMsg.put("status", 1);
        String msg = context.getMessage("ajax.copy.ok", null, locale);
        jsonMsg.put("msg", msg);
        return jsonMsg;
    }

    @GetMapping(value="/prepareCutFiles")
    @ResponseBody
    public Map prepareCutFiles(FormCommand command, HttpServletRequest request) {
        log.debug("Requesting prepareCutFiles");
        Locale locale = request.getLocale();
        basketSession.setDirsToCopy(pathEncodingUtils.decodeDirs(command.getDirs()));
        basketSession.setGoal("cut");
        Map jsonMsg = new HashMap();
        jsonMsg.put("status", 1);
        String msg = context.getMessage("ajax.cut.ok", null, locale);
        jsonMsg.put("msg", msg);
        return jsonMsg;
    }

    @GetMapping(value="/pastFiles")
    @ResponseBody
    public Map pastFiles(String dir, HttpServletRequest request) {
        log.debug("Requesting pastFiles");
        Locale locale = request.getLocale();
        dir = pathEncodingUtils.decodeDir(dir);
        Map jsonMsg = new HashMap();
        if(this.serverAccess.moveCopyFilesIntoDirectory(dir, basketSession.getDirsToCopy(), "copy".equals(basketSession.getGoal()))) {
            jsonMsg.put("status", 1);
            String msg = context.getMessage("ajax.paste.ok", null, locale);
            jsonMsg.put("msg", msg);
        }
        else {
            jsonMsg.put("status", 0);
            String msg = context.getMessage("ajax.paste.failed", null, locale);
            jsonMsg.put("msg", msg);
        }
        return jsonMsg;
    }

    @PostMapping(value="/authenticate")
    @ResponseBody
    public Map authenticate(String dir, String username, String password, HttpServletRequest request) {
        log.debug("Requesting authenticate");
        Locale locale = request.getLocale();
        dir = pathEncodingUtils.decodeDir(dir);
        Map jsonMsg = new HashMap();
        if(this.serverAccess.authenticate(dir, username, password)) {
            jsonMsg.put("status", 1);
            String msg = context.getMessage("auth.ok", null, locale);
            jsonMsg.put("msg", msg);
        }
        else {
            jsonMsg.put("status", 0);
            String msg = context.getMessage("auth.bad", null, locale);
            jsonMsg.put("msg", msg);
        }
        return jsonMsg;
    }

    @GetMapping(value="/fetchImage")
    public void fetchImage(@RequestParam("dir") String dir,
                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Requesting fetchImage");
        dir = pathEncodingUtils.decodeDir(dir);
        //this.serverAccess.updateUserParameters(dir);
        DownloadFile file = this.serverAccess.getFile(dir);
        response.setContentType(file.getContentType());
        if(file.getSize() > 0) {
            response.setContentLength((int)file.getSize());
        }
        FileCopyUtils.copy(file.getInputStream(), response.getOutputStream());
    }

    @GetMapping(value="/fetchSound")
    public void fetchSound(String dir,
                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Requesting fetchSound");
        dir = pathEncodingUtils.decodeDir(dir);
        DownloadFile file = this.serverAccess.getFile(dir);
        final String contentType = "audio/mpeg3";
        response.setContentType(contentType);
        if(file.getSize() > 0) {
            response.setContentLength((int)file.getSize());
        }
        FileCopyUtils.copy(file.getInputStream(), response.getOutputStream());
    }

    @GetMapping(value="/downloadFile")
    public void downloadFile(@RequestParam String dir,
                             HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Requesting downloadFile");
        dir = pathEncodingUtils.decodeDir(dir);
        DownloadFile file = this.serverAccess.getFile(dir);
        response.setContentType(file.getContentType());
        if(file.getSize() > 0) {
            response.setContentLength((int)file.getSize());
        }
        response.setHeader("Content-Disposition","attachment; filename=\"" + file.getBaseName() +"\"");
        FileCopyUtils.copy(file.getInputStream(), response.getOutputStream());
    }

    @PostMapping(value="/downloadZip")
    public void downloadZip(FormCommand command,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Requesting toggleThumbnailMode");
        List<String> dirs = pathEncodingUtils.decodeDirs(command.getDirs());
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition","attachment; filename=\"export.zip\"");
        this.serverAccess.writeZip(response.getOutputStream(), dirs);
    }


    // thanks to use BindingResult if FileUpload failed because of XHR request (and not multipart)
    // this method is called anyway
    @PostMapping(value="/uploadFile")
    public  ModelAndView uploadFile(String dir, FileUpload file, BindingResult result, HttpServletRequest request, UploadActionType uploadOption) throws IOException {

        log.debug("Requesting uploadFile");
        dir = pathEncodingUtils.decodeDir(dir);

        UploadActionType option = this.uploadActionOnExistingFileNameServlet;
        if (uploadOption != null) {
            option = uploadOption;
        }

        String filename;
        InputStream inputStream;

        if(file.getQqfile() != null) {
            // standard multipart form upload
            filename = file.getQqfile().getOriginalFilename();
            inputStream = file.getQqfile().getInputStream();
        } else {
            // XHR upload
            filename = request.getParameter("qqfile");
            inputStream = request.getInputStream();
        }
        return upload(dir, filename, inputStream, request.getLocale(), option);
    }


    // take care : we don't send json like application/json but like text/html !
    // goal is that the json is written in a frame
    private ModelAndView upload(String dir, String filename, InputStream inputStream, Locale locale, UploadActionType uploadOption) {
        boolean success = true;
        String text = "";
        try {
            if (this.serverAccess.putFile(dir, filename, inputStream, uploadOption)) {
                String msg = context.getMessage("ajax.upload.ok", null, locale);
                text = "{'success':'true', 'msg':'".concat(msg).concat("'}");
                log.info("upload file '{}' in '{}' ok", filename, dir);
            } else {
                success = false;
                log.info("error uploading file '{}' in '{}'", filename, dir);
            }
        } catch (Exception e) {
            log.error("error uploading file '{}' in '{}", filename, dir, e);
            success = false;
        }
        if(!success) {
            String msg = context.getMessage("ajax.upload.failed", null, locale);
            text = "{'success':'false', 'msg':'".concat(msg).concat("'}");
        }
        ModelMap model = new ModelMap("text", text);
        return new ModelAndView("text", model);
    }


    /**
     * Return the correct details view based on the requested file(s)
     */
    @PostMapping(value="/detailsArea")
    public ModelAndView detailsArea(FormCommand command,
                                    HttpServletRequest request, HttpServletResponse response) {
        log.debug("Requesting detailsArea");
        ModelMap model = new ModelMap();

        model.put("datePattern", context.getMessage("datePattern", null, request.getLocale()));

        if (command == null || pathEncodingUtils.decodeDirs(command.getDirs()) == null) {
            return new ModelAndView("details_empty", model);
        }

        // See if we go to the multiple files/folder view or not
        if (pathEncodingUtils.decodeDirs(command.getDirs()).size() == 1) {
            String path = pathEncodingUtils.decodeDirs(command.getDirs()).get(0);

            // get resource with folder details (if it's a folder ...)
            JsTreeFile resource = this.serverAccess.get(path, true, true);
            pathEncodingUtils.encodeDir(resource);

            // Based on the resource type, direct to appropriate details view
            if ("folder".equals(resource.getType()) || "drive".equals(resource.getType())) {
                Quota quota = this.serverAccess.getQuota(path);
                if(quota != null)
                    model.put("quota", quota);
                model.put("file", resource);
                return new ModelAndView("details_folder", model);
            } else if ("file".equals(resource.getType())) {
                model.put("file", resource);
                ResourceUtils.Type fileType = resourceUtils.getType(resource.getTitle());
                if (fileType == Type.AUDIO && !resource.isOverSizeLimit()) {
                    return new ModelAndView("details_sound", model);
                } else if (fileType == Type.IMAGE
                        && !resource.isOverSizeLimit()) {
                    return new ModelAndView("details_image", model);
                } else {
                    // generic file page
                    return new ModelAndView("details_file", model);
                }
            }
        } else if (pathEncodingUtils.decodeDirs(command.getDirs()).size() > 1) {
            // Add data for multiple files details view
            model.put("numselected", pathEncodingUtils.decodeDirs(command.getDirs()).size());

            // Find the resources which are files and add them to the
            // image_paths array
            List<String> image_paths = new ArrayList<String>();

            for (String filePath : pathEncodingUtils.decodeDirs(command.getDirs())) {
                JsTreeFile resource = this.serverAccess.get(filePath, false, true);
                ResourceUtils.Type fileType = resourceUtils.getType(resource.getTitle());
                if (fileType == Type.IMAGE && !resource.isOverSizeLimit()) {
                    image_paths.add(pathEncodingUtils.encodeDir(filePath));
                }
            }
            model.put("image_paths", image_paths);
            return new ModelAndView("details_files", model);
        }

        // Unknown resource type
        return new ModelAndView("details_empty", model);

    }

    @PostMapping(value="/getParentPath")
    @ResponseBody
    public String getParentPath(String dir,
                                      HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        log.debug("Requesting getParentPath");

        dir = pathEncodingUtils.decodeDir(dir);
        String parentDir;

        LinkedHashMap parentsPathesMap = pathEncodingUtils.getParentsPathes(dir, null, null);
        List<String> parentsPathes = new ArrayList<>(parentsPathesMap.keySet());
        if(parentsPathes.size()<2) {
            parentDir = this.serverAccess.getJsTreeFileRoot().getPath();
        } else {
            parentDir = parentsPathes.get(parentsPathes.size() - 2);
            if(!parentDir.endsWith("/")) {
                parentDir += "/";
            }
        }

        String parentDirEnc = pathEncodingUtils.encodeDir(parentDir);

        return parentDirEnc;
    }
}
