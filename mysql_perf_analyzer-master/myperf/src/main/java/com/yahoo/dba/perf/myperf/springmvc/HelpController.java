/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 表示help controller相关的类型。
 */
public class HelpController extends AbstractController {

    /**
     * 执行handle request internal操作。
     *
     * @param req  req参数。
     * @param resp resp参数。
     * @return 处理结果。
     */
    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest req,
                                                 HttpServletResponse resp) throws Exception {

        String key = req.getParameter("key");

        if (key == null || key.trim().length() == 0)
            key = "about";

        ModelAndView mv = new ModelAndView("help/" + key);
        return mv;

    }
}
