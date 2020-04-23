package com.wuwind.conn;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import frpclib.Frpclib;

/**
 * Created by wuhf on 2020/4/23.
 * Description ：
 */
public class FrpcManager {

    private UserLoginTask mAuthTask = null;
    private String FILENAME = "config.ini";
    private Context context;

    public FrpcManager(Context context) {
        this.context = context.getApplicationContext();
        copyToSD("120.79.223.156", "7000", "");
    }

    /**
     * @param service_ip
     * @param service_port
     * @param service_token 向配置文件中写入FRP服务器的参数
     */
    private void copyToSD(String service_ip, String service_port, String service_token) {
        InputStream in = null;
        FileOutputStream out = null;

        //判断如果数据库已经拷贝成功，不需要再次拷贝
        File file = new File(context.getExternalFilesDir(null), FILENAME);
        if (file.exists()) {
            file.delete();
        }
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//                AssetManager assets = getAssets();
//                //2.读取数据资源
//                in = assets.open(FILENAME);
//                out = new FileOutputStream(file);
//                //3.读写操作
//                byte[] b = new byte[1024];//缓冲区域
//                int len = -1; //保存读取的长度
//                while ((len = in.read(b)) != -1) {
//                    out.write(b, 0, len);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        try {
            out = new FileOutputStream(file, true);
            String common = "[common]\r\n";
            String server_addr = "server_addr = " + service_ip + "\r\n";
            String server_port = "server_port = " + service_port + "\r\n";
            String token = "token = " + service_token + "\r\n";
            String admin_addr = "admin_addr = 0.0.0.0" + "\r\n";
            String admin_port = "admin_port = 7400" + "\r\n";
            String admin_user = "admin_user = admin" + "\r\n";
            String admin_pwd = "admin_pwd = admin" + "\r\n";
            String log_file = "log_file = /storage/emulated/0/Android/data/com.frp.fun/files/frpc.log" + "\r\n";
            String log_level = "log_level = info" + "\r\n";
            String log_max_days = "log_max_days = 3" + "\r\n";
            String pool_count = "pool_count = 5" + "\r\n";
            String tcp_mux = "tcp_mux = true" + "\r\n";
            String login_fail_exit = "login_fail_exit = true" + "\r\n";
            String protocol = "protocol = tcp" + "\r\n";
            out.write(common.getBytes());
            out.write(server_addr.getBytes());
            out.write(server_port.getBytes());
            out.write(token.getBytes());
            out.write(admin_addr.getBytes());
            out.write(admin_port.getBytes());
            out.write(admin_user.getBytes());
            out.write(admin_pwd.getBytes());
            out.write(log_file.getBytes());
            out.write(log_level.getBytes());
            out.write(log_max_days.getBytes());
            out.write(pool_count.getBytes());
            out.write(tcp_mux.getBytes());
            out.write(login_fail_exit.getBytes());
            out.write(protocol.getBytes());

            String socket_name = "[f66]\n";
            String socket_type = "type = tcp\r\n";
            String socket_ip = "local_ip = " + "172.18.6.6" + "\r\n";
            String socket_port = "local_port =  " + "6111" + "\r\n";
            String socket_remoteport = "remote_port = " + "6003" + "\r\n";
            String use_encryption = "use_encryption = " + "false" + "\r\n";
            String use_compression = "use_compression = " + "false" + "\r\n";
            out.write(socket_name.getBytes());
            out.write(socket_type.getBytes());
            out.write(socket_ip.getBytes());
            out.write(socket_port.getBytes());
            out.write(socket_remoteport.getBytes());
            out.write(use_encryption.getBytes());
            out.write(use_compression.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void frpStart() {
        if (mAuthTask != null) {
            return;
        }
        mAuthTask = new UserLoginTask(context.getExternalFilesDir(null) + "/config.ini");
        Log.e("path", context.getExternalFilesDir(null) + "/config.ini");//打印
        mAuthTask.execute((Void) null);//执行异步线程
    }

    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mConfigPath;

        UserLoginTask(String email) {
            mConfigPath = email;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                Frpclib.run(mConfigPath);
            } catch (Throwable e) {
                if (e != null && e.getMessage() != null) {
                    Log.e("throwable", e.getMessage() + "");
                }

            }
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            //由于Frpclib.run(mConfigPath)该方法保持了长连接，所以这些方法都走不进去，只是摆设
            if (success) {
            } else {
            }
            mAuthTask = null;
//            finish();
        }

        @Override
        protected void onCancelled() {
            //由于Frpclib.run(mConfigPath)该方法保持了长连接，所以这些方法都走不进去，只是摆设，但退出程序会走这个方法
            Log.e("onCancelled", "+++++++");
            mAuthTask = null;
        }
    }
}
