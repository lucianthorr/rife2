/*
 * Copyright 2001-2022 Geert Bevin <gbevin[remove] at uwyn dot com>
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import rife.config.RifeConfig;
import rife.engine.Site;
import rife.tools.FileUtils;

import java.io.IOException;

public class RifeFilter implements Filter {
    public static String RIFE_SITE_CLASS_NAME = "rifeSiteClass";

    private String gateUrl_ = null;
    private Site site_ = null;

    public Site site() {
        return site_;
    }

    public void site(Site site) {
        site_ = site;
    }

    @Override
    public void init(FilterConfig config)
    throws ServletException {
        var classloader = getClass().getClassLoader();

        var site_classname = config.getInitParameter(RIFE_SITE_CLASS_NAME);
        if (site_classname != null) {
            try {
                var site_class = classloader.loadClass(site_classname);
                site_ = (Site) site_class.getDeclaredConstructor().newInstance();
            } catch (Throwable e) {
                throw new ServletException(e);
            }
        }

        if (site_ != null) {
            site_.setup();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
        if (request instanceof HttpServletRequest http_servlet_request &&
            response instanceof HttpServletResponse http_servlet_response) {
            try {
                String request_uri = http_servlet_request.getRequestURI();
                String extension = FileUtils.getExtension(request_uri);

                // check if the url matches one of the pass-through suffixes
                boolean passthrough = extension != null &&
                    RifeConfig.engine().getPassThroughSuffixes().contains(extension);

                // if not passed through, handle the request
                if (!passthrough) {
                    // create the servlet path
                    if (null == gateUrl_) {
                        var context_path = http_servlet_request.getContextPath();

                        // ensure a valid context path
                        if (context_path != null &&
                            !context_path.equals(".") &&
                            !context_path.equals("/")) {
                            gateUrl_ = context_path;
                        } else {
                            gateUrl_ = "";
                        }
                    }

                    final var element_url = request_uri.substring(gateUrl_.length());
                    final var http_request = new HttpRequest(http_servlet_request);
                    final var http_response = new HttpResponse(http_request, http_servlet_response);
                    http_request.init();
                    if (site_.process(gateUrl_, element_url, http_request, http_response)) {
                        return;
                    }
                }
            } catch (Throwable e) {
                throw new ServletException(e);
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
