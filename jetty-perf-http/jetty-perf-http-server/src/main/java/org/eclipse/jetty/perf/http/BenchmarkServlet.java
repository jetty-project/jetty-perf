//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.perf.http;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BenchmarkServlet extends HttpServlet
{
    public static final String X_CONTENT_LENGTH = "X-Content-Length";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        ServletInputStream input = request.getInputStream();
        int offset = 0;
        byte[] timestamp = new byte[8];
        int length = timestamp.length;
        while (true)
        {
            offset += input.read(timestamp, offset, length - offset);
            if (offset == length)
                break;
        }

        int download = request.getIntHeader(X_CONTENT_LENGTH);
        response.setContentLength(timestamp.length + download);

        ServletOutputStream output = response.getOutputStream();
        output.write(timestamp);

        byte[] bytes = new byte[1024];
        while (download > 0)
        {
            if (download >= bytes.length)
            {
                output.write(bytes);
                download -= bytes.length;
            }
            else
            {
                output.write(new byte[download]);
                download = 0;
            }
        }

        // Close to be sure that we account for the last write time here
        // and not deep buried in container code where won't be measured.
        output.close();
    }
}
