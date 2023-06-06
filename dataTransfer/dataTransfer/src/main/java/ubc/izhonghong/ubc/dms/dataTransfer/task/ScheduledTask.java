package ubc.izhonghong.ubc.dms.dataTransfer.task;


import com.csvreader.CsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ubc.izhonghong.ubc.dms.dataTransfer.utils.JDBCUtils;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;

@Component
public class ScheduledTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTask.class);

    // 规定批处理的size
    private static final int BATCH_SIZE = 50;

    // 待备份的数据库名单文件名
    private static final String DB_CSV_FILENAME = "includedDatabases.csv";
    // 指定数据库中那些表需要备份的文件
    private static final String TABLES_CSV_FILENAME = "includedTables.csv";


    private static final String JDBC_PROPERTIES_NAME = "jdbc.properties";

    //
    private static final String MASTER_PREFIX = "from";

    private static final String SLAVE_PREFIX = "to";

    private static final String FAILURE_LOG_DB_NAME = "failure_log";


    //private String[] schemas = new String[]{"ums_standard_guangming", "dms_standard_guangming", "led_manager", "mng_localization_guangming", "mng_localization_business_guangming", "yanshi"};
    private static String[] schemas;

    // 排除一些不需要备份的表
    private static Set<String> includeTablesSet;




    // 返回resources目录下的某一个资源的路径，以方便读取文件
    public static String getPathOfReources(String fileName) {
        ClassLoader classLoader = ScheduledTask.class.getClassLoader();
        URL db_resources  = classLoader.getResource(fileName);
        return db_resources.getPath();
    }

    // 静态代码块，类静态代码块随着类的加载而加载，且只执行一次。
    static {
        //获取src路径下的文件的方式--->可以使用ClassLoader 类加载器

//        ClassLoader classLoader = ScheduledTask.class.getClassLoader();
//        URL db_resources  = classLoader.getResource("includedDatabases.csv");
//        String db_path = db_resources.getPath();
        String db_path = getPathOfReources(DB_CSV_FILENAME);
        Set<String> dbSet = readCsvByCsvReader(db_path);
        schemas = new String[dbSet.size()];

        // 将待迁移的命名空间加入到String类型的数组当中
        for (int i = 0; i < dbSet.size(); i++) {
            schemas[i] = (String) dbSet.toArray()[i];
        }

//        URL table_resources  = classLoader.getResource("includedTables.csv");
//        String table_path = table_resources.getPath();

        String table_path = getPathOfReources(TABLES_CSV_FILENAME);
        includeTablesSet = readCsvByCsvReader(table_path); // 将待迁移的表加入到hashset中

        LOGGER.info("-----------------------初始化工作已完成---------------------");
    }

    // 通过读取csv文件获取相关的配置文件的数据
    public static Set<String> readCsvByCsvReader(String filePath) {
        Set<String> strList = null;
        try {
            ArrayList<String[]> arrList = new ArrayList<String[]>();
            strList = new HashSet<String>();
            CsvReader reader = new CsvReader(filePath, ',', Charset.forName("UTF-8"));
            while (reader.readRecord()) {
                // System.out.println(Arrays.asList(reader.getValues()));
                arrList.add(reader.getValues()); // 按行读取，并把每一行的数据添加到list集合
            }
            reader.close();
            System.out.println("读取的行数：" + arrList.size());
            // 如果要返回 String[] 类型的 list 集合，则直接返回 arrList
            // 以下步骤是把 String[] 类型的 list 集合转化为 String 类型的 list 集合
            for (int row = 0; row < arrList.size(); row++) {
                // 组装String字符串
                // 如果不知道有多少列，则可再加一个循环
                String ele = arrList.get(row)[0];
                System.out.print("========================"+ ele + "==");
                strList.add(ele);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strList;
    }


    // 查询对应一个数据库空间中的所有表名
    public String[] getTables(Connection connection, String schema) throws SQLException {
        Statement statement = connection.createStatement();
        // 注意这里的命名空间一定要被单引号括起来，否则会报错unknown cloumn错误（应该是schema已经成为了数据库）
        ResultSet resultSet = statement.executeQuery("select TABLE_NAME FROM information_schema.TABLES WHERE table_schema = "+ "'" + schema + "'" + " GROUP BY TABLE_NAME");
        resultSet.last(); // 定位至结果集的最后一行
        String[] res = new String[resultSet.getRow()];
        LOGGER.info("row: {}", resultSet.getRow());
        resultSet.beforeFirst(); // 游标复位到数据集前的位置

        int i = 0;
        while (resultSet.next()) {
            res[i] = (String) resultSet.getObject(1);
            i++;
        }
        statement.close(); // 关键声明
        return res;
    }

    // 插入失败后的处理机制
    public void handleFailures(ResultSet resultSet, int[] states, String dbName, String tableName) throws SQLException, IOException {


        for (int i = 0; i < states.length; i++) {
            if (states[i] == 1) {
                continue;
            }
            // 如果不为1，则说明会此次批处理中的这一条插入失败，先尝试重新插入操作
            resultSet.relative(-(states.length - i)); // 利用相对定位，定位至插入失败的这一行
            // 如果依然插入失败则需要将这条记录相关的内容存起来，包括这个记录的id，所在表，主库所在命名空间，从库所在的命名空间
            String id = (String) resultSet.getObject(1);

            // 连接至插入日志数据库
            JDBCUtils jdbcUtils = new JDBCUtils(MASTER_PREFIX + "-" + FAILURE_LOG_DB_NAME + "-");
            Connection connection = jdbcUtils.getConnection();
            String insertSql = "insert into guangming_failure_log (master_db_url, `table_name`, slave_db_url, fail_record_id, db_name, created_time, updated_time, deleted, num_failure, isSuccessfull)values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement psInsertLog = connection.prepareStatement(insertSql);
            psInsertLog.setObject(1, null);
            psInsertLog.setObject(2, tableName);
            psInsertLog.setObject(3, null);
            psInsertLog.setObject(4, id);
            psInsertLog.setObject(5, dbName);
            psInsertLog.setObject(6, null);
            psInsertLog.setObject(7, null);
            psInsertLog.setObject(8, tableName);
            psInsertLog.setObject(9, 2);
            psInsertLog.setObject(10, 0);

        }
    }
    // 使用原生的prepareStatement执行数据备份过程
    // 每10秒执行一次，从第0s开始执行
    @Scheduled(cron = "0/10 * * * * ? ")
    public void testPreparedStatement() throws SQLException, IOException {

        LOGGER.info("------------------------下面执行拷贝操作：----------------------------");

        for (int s = 0; s < schemas.length; s++) {
            String schema = schemas[s];
            LOGGER.info("=========================================开始迁移数据库：{}============================================", schema);
            JDBCUtils jdbcFrom = new JDBCUtils(MASTER_PREFIX + "-" + schema + "-" );
            LOGGER.info("------------ 成功建立至主库的连接 -----------");

            Connection connFrom = jdbcFrom.getConnection();

            JDBCUtils jdbcTo = new JDBCUtils(SLAVE_PREFIX + "-" + schema + "-");
            Connection connTo = jdbcTo.getConnection(); // 建立与目标节点的连接
            LOGGER.info("------------ 成功建立至备库的连接 -----------");

            String[] tables = getTables(connFrom, schema);
            LOGGER.info("该命名空间有多少张表：{}", tables.length);
            for (int t = 0; t < tables.length; t++) {

                String tableName = tables[t];

                if (!includeTablesSet.contains(tableName)) {
                    // 如果不是待迁移的表，则判断下一张表
                    LOGGER.info("跳过表: {}", tableName);
                    continue;
                }

                String querySql = "select * from " + tableName;

                // 预编译SQL语句
                PreparedStatement psQueryfrom = connFrom.prepareStatement(querySql);

                //3. 执行
                ResultSet resultSet = psQueryfrom.executeQuery();
                int columnCount = resultSet.getMetaData().getColumnCount(); // 一条记录有多少个字段.
                resultSet.last(); // 将光标定位到结果集的最后一行，以计算总函数
                int rowCount = resultSet.getRow(); // 取得当前行的行号（从1开始计算），以获取结果集的总行数
                LOGGER.info("总行数： {}", rowCount);

                resultSet.beforeFirst(); // 将光标定位到第一行之前

                LOGGER.info("------------插入数据之前先清空备库目标表：-------------");
                String truncateSql = "delete from " + tableName;
                PreparedStatement psTruncateTo = connTo.prepareStatement(truncateSql);
                int isTruncated = psTruncateTo.executeUpdate();

                LOGGER.info("------------共清除{}条数据-------------", isTruncated);

                connTo.setAutoCommit(false);
                // 拼接插入语句
                StringBuilder insertSql = new StringBuilder("insert into " + tableName + " values("); // 拼凑出sql

                for (int i = 0; i < columnCount - 1; i++) {
                    insertSql.append("?,");
                }
                insertSql.append("?)");
                PreparedStatement psInsertTo = connTo.prepareStatement(insertSql.toString());

                int batchCount = 0;
                // 开始遍历每一行数据
                while (resultSet.next()) {

                    // 使用Object数组接收一行数据
                    Object [] oneRecord = new Object[columnCount];
                    for (int r = 0; r < columnCount; r++) {
                        oneRecord[r] = resultSet.getObject(r + 1); // 将数据赋值给Object数组对象
//                        String columnName = resultSet.getMetaData().getColumnName(r + 1); // 获取index位置上的列名
//                        oneRecord[r] = resultSet.getObject(columnName); // 将某一个列名对应的数据赋值给Object[i]
                    }

                    LOGGER.info("记录：{}", Arrays.toString(oneRecord));

                    for (int i = 0; i < columnCount; i++) {
                        psInsertTo.setObject(i + 1, oneRecord[i]);
                    }

                    psInsertTo.addBatch();
                    //每500条执行一次，避免内存不够的情况
                    if (resultSet.getRow() % BATCH_SIZE == 0) {
                        LOGGER.info("------------------------下面执行批量插入操作：----------------------------");
                        int[] states = psInsertTo.executeBatch(); // 批处理插入, states数组接受状态， states[i]为1则表明批处理中的这条记录是插入成功的
                        // 如果不想出错后，完全没保留数据，则可以每执行一次批处理操作时提交一次事务，但得保证数据不会重复，同时得保证出错后，是这一次的差错怎么处理
                        // 或者直到所有的数据都写至备库之前才提交事务，
                        connTo.commit();
                        batchCount++; // 批处理次数自增
                        LOGGER.info("--------------------- 检测批处理是否成功：-----------------------");
                        connTo.setAutoCommit(true);//在把自动提交打开

                        handleFailures(resultSet, states, schema, tableName);

                        // 提交之后，检查批处理的数据状态, 针对产生错误的记录，要做相应的处理
//                        for (int i = 0; i < states.length; i++) {
//                            if (states[i] != 1) {
//                                // 先尝试重新插入操作
//                                resultSet.relative(-(states.length - i)); // 定位至插入失败的这一行
//                                for (int r = 0; r < columnCount; r++) {
//                                    oneRecord[r] = resultSet.getObject(r + 1); // 将数据赋值给Object数组对象
//                                }
//                                for (int j = 0; j < columnCount; j++) {
//                                    psInsertTo.setObject(j + 1, oneRecord[j]);
//                                }
//                                int isInserted = psInsertTo.executeUpdate();
//
//                                String id = (String) resultSet.getObject(1); // 要记下当前是第几批次，好精确计算出哪一条数据插入失败
//                                System.out.println("=+++++++++++++++++打印批处理状态：" + states[i] + "+++++++++++++++++++");
//
//                                if () {
//                                    // 如果依然插入失败则
//
//                                }
//                                // 如果不为1，则说明会此次批处理中的这一条插入失败，需要将这条记录相关的内容存起来，包括这个记录的id，所在表，主库所在命名空间，从库所在的命名空间
//
//                            }
//
//                        }

                        LOGGER.info("------------插入成功-------------");
                    }

                }

                psInsertTo.executeBatch();//执行最后剩下不够500条的
                connTo.commit();//执行完后，手动提交事务
                batchCount++; // 批处理次数自增
                LOGGER.info("------------插入成功-------------");

                connTo.setAutoCommit(true);//在把自动提交打开
                psInsertTo.close();

                psQueryfrom.close();
            }
            connTo.close();
            connFrom.close();
        }
    }



    // 使用原生的prepareStatement执行数据备份过程
    // 每10秒执行一次，从第0s开始执行
    @Scheduled(cron = "0/10 * * * * ? ")
    public void testPreparedStatement2() throws SQLException {
        LOGGER.info("------------------------下面执行拷贝操作：----------------------------");

        for (int s = 0; s < schemas.length; s++) {
            String schema = schemas[s];
            LOGGER.info("=========================================开始迁移数据库：{}============================================", schema);
            JDBCUtils jdbcFrom = new JDBCUtils("from-" + schema + "-" );
            LOGGER.info("------------ 成功建立至主库的连接 -----------");

            Connection connFrom = jdbcFrom.getConnection();
            //connFrom.setAutoCommit(false);

            JDBCUtils jdbcTo = new JDBCUtils("to-" + schema + "-");
            Connection connTo = jdbcTo.getConnection(); // 建立与目标节点的连接
            LOGGER.info("------------ 成功建立至备库的连接 -----------");

            String[] tables = getTables(connFrom, schema);
            LOGGER.info("该命名空间有多少张表：{}", tables.length);
            for (int t = 0; t < tables.length; t++) {

                String tableName = tables[t];

                if (!includeTablesSet.contains(tableName)) {
                    // 如果不是待迁移的表，则判断下一张表
                    LOGGER.info("跳过表: {}", tableName);
                    continue;
                }

                String querySql = "select * from " + tableName;

                // 预编译SQL语句
                PreparedStatement psQueryfrom = connFrom.prepareStatement(querySql);

                //3. 执行
                ResultSet resultSet = psQueryfrom.executeQuery();
                int columnCount = resultSet.getMetaData().getColumnCount(); // 一条记录有多少个字段.
                resultSet.last(); // 将光标定位到结果集的最后一行，以计算总函数
                int rowCount = resultSet.getRow(); // 取得当前行的行号（从1开始计算），以获取结果集的总行数
                LOGGER.info("总行数： {}", rowCount);

                resultSet.beforeFirst(); // 将光标定位到第一行之前

                LOGGER.info("------------插入数据之前先清空备库目标表：-------------");
                String truncateSql = "delete from " + tableName;
                PreparedStatement psTruncateTo = connTo.prepareStatement(truncateSql);
                int isTruncated = psTruncateTo.executeUpdate();

                LOGGER.info("------------共清除{}条数据-------------", isTruncated);

                connTo.setAutoCommit(false);
                // 拼接插入语句
                StringBuilder insertSql = new StringBuilder("insert into " + tableName + " values("); // 拼凑出sql

                for (int i = 0; i < columnCount - 1; i++) {
                    insertSql.append("?,");
                }
                insertSql.append("?)");
                PreparedStatement psInsertTo = connTo.prepareStatement(insertSql.toString());


                // 开始遍历每一行数据
                while (resultSet.next()) {

                    // 使用Object数组接收一行数据
                    Object [] oneRecord = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        String columnName = resultSet.getMetaData().getColumnName(i + 1); // 获取index位置上的列名
                        oneRecord[i] = resultSet.getObject(columnName); // 将某一个列名对应的数据赋值给Object[i]
                    }

                    LOGGER.info("记录：{}", Arrays.toString(oneRecord));

                    for (int i = 0; i < columnCount; i++) {
                        psInsertTo.setObject(i + 1, oneRecord[i]);
                    }

                    psInsertTo.addBatch();
                    //每500条执行一次，避免内存不够的情况
                    if (resultSet.getRow() % 50 == 0) {
                        LOGGER.info("------------------------下面执行批量插入操作：----------------------------");
                        int[] states = psInsertTo.executeBatch(); // 批处理插入, states数组接受状态， states[i]为1则表明批处理中的这条记录是插入成功的

                        for (int i = 0; i < states.length; i++) {
                            System.out.println("=+++++++++++++++++打印批处理状态：" + states[i] + "+++++++++++++++++++");
                        }
                        // 如果不想出错后，完全没保留数据，则可以每执行一次批处理操作时提交一次事务，但得保证数据不会重复，同时得保证出错后，是这一次的差错怎么处理
                        // 或者直到所有的数据都写至备库之前才提交事务，
                        connTo.commit();
                        //LOGGER.info("------------插入状态：{}-------------", isUpdated == 1 ? "插入成功" : "插入失败");
                        LOGGER.info("------------插入成功-------------");
                    }

                }

                psInsertTo.executeBatch();//执行最后剩下不够500条的
                connTo.commit();//执行完后，手动提交事务
                LOGGER.info("------------插入成功-------------");

                connTo.setAutoCommit(true);//在把自动提交打开
                psInsertTo.close();

                psQueryfrom.close();
            }
            connTo.close();
            connFrom.close();
        }
    }




}
