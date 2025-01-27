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

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.esupportail.filemanager.beans.JsTreeFile;
import org.esupportail.filemanager.services.IServersAccessService;
import org.esupportail.filemanager.services.UserAgentInspector;
import org.esupportail.filemanager.utils.PathEncodingUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequestMapping(value = {"/", ""})
public class HtmlController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HtmlController.class);

    @Resource
    UserAgentInspector userAgentInspector;

    @Resource
    PathEncodingUtils pathEncodingUtils;

    @Resource
    IServersAccessService serverAccess;

    @Resource
    ApplicationContext context;

    @GetMapping
    public String browse(Model model, @RequestParam(required = false) String dir, HttpServletRequest request) {
        if(!StringUtils.hasText(dir)) {
            dir = JsTreeFile.ROOT_DRIVE;
            dir = pathEncodingUtils.encodeDir(dir);
        }
        model.addAttribute("defaultPath", dir);
        if(userAgentInspector.isMobile(request)) {
            log.debug("Mobile user agent detected, redirecting to mobile view");
            return browseMobile(model, dir, request);
        }
        return "view";
    }

    public String browseMobile(Model model, @RequestParam(required = false) String dir, HttpServletRequest request) {
        String decodedDir = pathEncodingUtils.decodeDir(dir);
        JsTreeFile resource = this.serverAccess.get(decodedDir, false, false);
        pathEncodingUtils.encodeDir(resource);
        model.addAttribute("resource", resource);
        List<JsTreeFile> files;
        if(decodedDir == null || dir.length() == 0 || dir.equals(JsTreeFile.ROOT_DRIVE) ) {
            files = this.serverAccess.getJsTreeFileRoots();
        } else {
            files = this.serverAccess.getChildren(decodedDir);
        }
        Collections.sort(files);
        pathEncodingUtils.encodeDir(files);
        model.addAttribute("files", files);
        model.addAttribute("currentDir", dir);
        LinkedHashMap parentsEncPathes = pathEncodingUtils.getParentsEncPathes(resource);
        model.addAttribute("parentsEncPathes", parentsEncPathes);
        model.addAttribute("datePattern", context.getMessage("datePattern", null, request.getLocale()));
        return "view-mobile";
    }
}
