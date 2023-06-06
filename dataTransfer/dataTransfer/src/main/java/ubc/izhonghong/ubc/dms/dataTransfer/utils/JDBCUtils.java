package ubc.izhonghong.ubc.dms.dataTransfer.utils;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.Properties;

public class JDBCUtils {


    private static String url;
    private static String user;
    private static String password;
    private static String driver;


    // new一个对象的时候根据不同的前缀读取jdbc配置文件中不同的属性和值
    public JDBCUtils(String prefix) {
        try {
            //1. 创建Properties集合类。
            Properties properties = new Properties();
            //获取src路径下的文件的方式--->ClassLoader 类加载器
            ClassLoader classLoader = JDBCUtils.class.getClassLoader();
            URL res  = classLoader.getResource("jdbc.properties");
            String path = res.getPath();
            //2. 加载文件
            properties.load(new FileReader(path));

            url = properties.getProperty(prefix + "url");
            user = properties.getProperty(prefix + "user");
            password = properties.getProperty(prefix + "password");
            driver = properties.getProperty(prefix + "driver");
            //注册驱动
            Class.forName(driver);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * jdbc.properties配置文件读取，只用执行一次，使用静态代码块
     */
//    static {
//
//        try {
//            //1. 创建Properties集合类。
//            Properties properties = new Properties();
//
//            //获取src路径下的文件的方式--->ClassLoader 类加载器
//            ClassLoader classLoader = JDBCUtils.class.getClassLoader();
//            URL res  = classLoader.getResource("jdbc.properties");
//            String path = res.getPath();
//            //2. 加载文件
//            properties.load(new FileReader(path));
//
//            url = properties.getProperty("from-url");
//            user = properties.getProperty("from-user");
//            password = properties.getProperty("from-password");
//            driver = properties.getProperty("from-driver");
//            //注册驱动
//            Class.forName(driver);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//    }


    /**
     * 获取连接
     * @return 连接对象
     */
    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(url, user, password);
    }




    /**
     * 释放资源
     * @param statement
     * @param connection
     */

    public static void close(Statement statement, Connection connection) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

    }

    /**
     * 释放资源
     * @param resultSet
     * @param statement
     * @param connection
     */
    public static void close(Statement statement, Connection connection, ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

}
