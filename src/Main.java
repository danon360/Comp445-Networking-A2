import java.io.*;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static boolean verbose;
    public static int port;
    public static String dir;

    private static String[] splitResponse(String in){
        String[] out = new String[2];
        int split;
        split = in.indexOf("\r\n\r\n");

        //adding the headers into the 1st index
        out[0] = in.substring(0,split);
        out[1] = in.substring(split + 4,in.length());   //+4 is to account for \r\n\rn\
        return out;
    }

    public static String read(InputStream IS) throws IOException {
        // Get response
        BufferedReader rd = new BufferedReader(new InputStreamReader(IS));
        String line = "";
        Scanner in = new Scanner(IS).useDelimiter("(\\b|\\B)");

        String temp;


        int tem;
        while(rd.ready() && (tem = rd.read()) != -1) {
            line += (char) tem;
            //System.out.println(line2);
        }

        return line;
    }
    public static List<String> listSubDir(String path){
        List<String> list = new ArrayList<String>();
        List<String> notAllowed = new ArrayList<String>();

        //adding current java files to the not allowed list
        notAllowed.add(System.getProperty("user.dir")+"/"+dir+"/"+Main.class.getCanonicalName() + ".java");
        notAllowed.add(System.getProperty("user.dir")+"/"+dir+"/" +Main.class.getName() + ".class");
        notAllowed.add(System.getProperty("user.dir")+"/"+dir+"/" +Main.class.getName() + ".jar");
        notAllowed.add(System.getProperty("java.class.path"));

        ClassLoader loader = Main.class.getClassLoader();
       notAllowed.add(loader.getResource("Main.class").toString().substring(5));
        //add current directory manually
        File temp = new File(dir);
        list.add(temp.getAbsolutePath());
        list.add(temp.getAbsolutePath()+"/");
        File file = new File(path);
        for (final File fileEntry : file.listFiles()) {
            //if the file/folder is in the not allowed list, dont add it
            if(notAllowed.contains(fileEntry.getAbsolutePath())) continue;
            list.add(fileEntry.getAbsolutePath());
        }
        return list;
    }
    public static String printDirectory(File file){
        String out = "";
        for (final File fileEntry : file.listFiles()) {
           out+= fileEntry.getName() + '\n';
        }
        //out += file.listFiles();
        return out;
    }
    public static int printFile(PrintWriter out, File file, int code){
        Scanner sc = null;
        String outFile = "";
        String temp;
        String body;
        String headers;
        String response;

       if(code == 200){
           try {
               sc = new Scanner(file);
               // we just need to use \\Z as delimiter
               sc.useDelimiter("\\Z");
               outFile =  sc.next();
           } catch (FileNotFoundException e) {

               code = 404;
           }
       }

        body = setBody(code,outFile);
        headers = setHeaders(code,body.length());
        response = headers + "\r\n"+body;
        out.write(response);

        return 0;
    }
    public static int getCode(String path,String method){
        int code = 200;
        File outFile = null;
        File file = new File(dir+path);
        List<String> dirs = listSubDir(dir);
        int Findex = path.indexOf("/");
        int Lindex = path.indexOf("/",Findex+1);



        //GET

        //404 not found
        if(!file.exists() && method.equalsIgnoreCase("get")) return 404;

        //403 not permited
        if(file.exists() && method.equalsIgnoreCase("get") && !file.canRead()) return 403;

        //POST

        //201 Created
        if(!file.exists()  && method.equalsIgnoreCase("post") ) return 201;
        //403 not permited
        if(file.exists() && method.equalsIgnoreCase("post") && !file.canWrite()) return 403;

        //GET and POST
        if(!dirs.contains(file.getAbsolutePath())) return 403;


        return code;
    }
    public static int post(String input, Socket s){
        String path = null;
        String headers;
        String cHeaders;
        String body;
        String response;
        int code = 200;
        String inputFile;
        PrintWriter out = null;
        PrintWriter inFile;

        if(verbose) System.out.println("Using the post method");

        try {
            out = new PrintWriter(s.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        int sIndex = input.indexOf(" ");
        path = input.substring(sIndex, input.indexOf(" ",sIndex+1)).trim();
       cHeaders = input.substring(input.indexOf("\r\n") +2,input.indexOf("\r\n\r\n"));
        inputFile = input.substring(input.indexOf("\r\n\r\n")+2);

        File file = new File(dir+path);

        code = getCode(path,"post");


        if(code < 300 && code >= 200){
            try {
                inFile = new PrintWriter(file);
                inFile.write(inputFile);
                inFile.flush();
                inFile.close();
            } catch (FileNotFoundException e) {
                code = 404;
            }
        }

        body = setBody(code,inputFile);
        headers = setHeaders(code,body.length());
        response = headers + "\r\n" + body;

        out.print(response);
        out.flush();
        return 0;
    }

    public static int get(String input, Socket s){
        String path = null;
        String headers;
        int code = 200;
        PrintWriter out = null;

        if(verbose) System.out.println("Using the Get method");

        try {
            out = new PrintWriter(s.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        int sIndex = input.indexOf(" ");
        path = input.substring(sIndex, input.indexOf(" ",sIndex+1)).trim();
        headers = input.substring(input.indexOf("\r\n") +2);

        File file = new File(dir+path);
        code = getCode(path,"get");


        if(file.isDirectory() && code == 200){
            if(verbose) System.out.println("given path is a directory.");
           out.print(printDirectory(file));
        }else{
            if(verbose) System.out.println("given path is a file");
            code = printFile(out,file,code);
        }
        out.close();
        return code;
    }

    public static String setHeaders(int statusCode, int contentLength){

        String out = "";
        String statusLine = "";

        switch (statusCode){
            case 200:
                statusLine = "HTTP/1.0 200 OK \r\n";
                break;
            case 201:
                statusLine = "HTTP/1.0 201 Created \r\n";
                break;
            case 202:
                break;
            case 204:
                break;
            case 400:
                statusLine = "HTTP/1.0 400 Bad Request\r\n";
                break;
            case 401:
                statusLine = "HTTP/1.0 401 Unauthorized\r\n";
                break;
            case 403:
                statusLine = "HTTP/1.0 402 Forbidden\r\n";
                break;
            case 404:
                statusLine = "HTTP/1.0 404 Not Found\r\n";
                break;
        }

        out += statusLine +
                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Expires: 0\r\n" ;
        return out;
    }
    public static String setBody(int code, String outFile){
        String body = "";
        switch (code){
            case 200:
                body = "HTTP/1.0 200 OK\r\n\n" + outFile;
                break;
            case 201:
                body = "HTTP/1.0 201 Created\r\n\n" + outFile;
                break;
            case 202:
                body = "HTTP/1.0 200 Accepted\r\n\n" + outFile;
                break;
            case 400:
                body = "<!-- <!DOCTYPE html> -->\n" +
                        "<html>\n" +
                        "<head> <title> Bad Request</title> <head>\n" +
                        "<body>\n" +
                        "<h1> 400 Bad Request </h1>>\n" +
                        "</body>\n" +
                        "</html>";
                break;
            case 401:
                body = "<!-- <!DOCTYPE html> -->\n" +
                        "<html>\n" +
                        "<head> <title> Unauthorised</title> <head>\n" +
                        "<body>\n" +
                        "<h1> 401 Unauthorised </h1>>\n" +
                        "</body>\n" +
                        "</html>";
                break;
            case 403:
                body = "<!-- <!DOCTYPE html> -->\n" +
                        "<html>\n" +
                        "<head> <title> Forbidden</title> <head>\n" +
                        "<body>\n" +
                        "<h1> 403 Forbidden </h1>>\n" +
                        "</body>\n" +
                        "</html>";
                break;
            case 404:
                body = "<!-- <!DOCTYPE html> -->\n" +
                        "<html>\n" +
                        "<head> <title> Page Not Found</title> <head>\n" +
                        "<body>\n" +
                        "<h1> 404 Page not found </h1>>\n" +
                        "</body>\n" +
                        "</html>";
                break;

        }
        return body+"\r\n";
    }

    public static void setArgs(String[] in){
        int pIndex;
        int dIndex;

        List<String> arguments = null;

        if (in.length == 0){
            verbose = false;
            pIndex =-1;
            dIndex = -1;
        }else{
           arguments = Arrays.asList(in);

            //setting verbose option
            if(arguments.contains("-v")) verbose = true;
            pIndex = arguments.indexOf("-p");
            dIndex = arguments.indexOf("-d");
        }

        if(pIndex == -1){
            port = 8080;
        }else port = Integer.parseInt(arguments.get(pIndex + 1));

        if(dIndex == -1){
            dir = "src";
        }else dir = arguments.get(dIndex + 1);

        if(verbose) System.out.println("Using port: " + port+ ", and directory: "+dir);
    }
    public static void usage(){
        System.out.println("httpfs is a simple file server.\n\r" +
                "usage: httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n\r" +
                "-v\tPrints debugging messages.\n\r" +
                "-p\tSpecifies the port number that the server will listen and serveat.Default is 8080.\n\r" +
                "-d\tSpecifies the directory that the server will use toread/write requested files. Default is the current directory when launching the application");
        System.exit(0);
    }
    public static void main(String[] args) {



        String input = null;
        String request;
        String command;
        String headers;
        String body;
        Socket clientSocket = null;


        setArgs(args);

        try {
            ServerSocket serverSocket = new ServerSocket(port);
             clientSocket = serverSocket.accept();
            PrintWriter out =
                    new PrintWriter(clientSocket.getOutputStream(), true);
            input = read(clientSocket.getInputStream());

            //System.out.println(in.readLine());
        }catch(java.io.IOException e){
            e.printStackTrace();
            System.err.println("err");
            System.exit(1);
        }

        request = input.substring(0,input.indexOf("\r\n"));


        if(request.contains("get") || request.contains("GET")){
            command = "get";
            get(input,clientSocket);
        }else{
            command = "post";
            post(input,clientSocket);
        }


    }
}