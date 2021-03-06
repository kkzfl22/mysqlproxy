package com.mysqlproxy.mysql.handler.frontend;

import com.mysqlproxy.Constants;
import com.mysqlproxy.ServerContext;
import com.mysqlproxy.buffer.MyByteBuff;
import com.mysqlproxy.mysql.BackendMysqlConnection;
import com.mysqlproxy.mysql.BackendMysqlConnectionFactory;
import com.mysqlproxy.mysql.FrontendMysqlConnection;
import com.mysqlproxy.mysql.MysqlConnection;
import com.mysqlproxy.mysql.codec.ErrorPacketEncoder;
import com.mysqlproxy.mysql.handler.StateHandler;
import com.mysqlproxy.mysql.protocol.ErrorPacket;
import com.mysqlproxy.mysql.state.ComQueryResponseState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class FrontendComQueryStateHandler implements StateHandler {
    private Logger logger = LoggerFactory.getLogger(FrontendComQueryStateHandler.class);

    public static final FrontendComQueryStateHandler INSTANCE = new FrontendComQueryStateHandler();

    private FrontendComQueryStateHandler() {
    }

    @Override
    public void handle(MysqlConnection connection, Object o) {
        FrontendMysqlConnection frontendMysqlConnection = (FrontendMysqlConnection) connection;
        BackendMysqlConnection backendMysqlConnection = frontendMysqlConnection.getBackendMysqlConnection();
        try {
            if (frontendMysqlConnection.getDirectTransferPacketWriteLen() != 0 &&
                    frontendMysqlConnection.isDirectTransferComplete()) {
                //透传完成，转换状态
                logger.debug("前端COM_QUERY透传完成，转换至下一状态");
                frontendMysqlConnection.setDirectTransferPacketLen(0);
                frontendMysqlConnection.setDirectTransferPacketWriteLen(0);
                frontendMysqlConnection.getReadBuffer().clear();
                frontendMysqlConnection.disableRead();
                frontendMysqlConnection.setState(ComQueryResponseState.INSTANCE);
            } else {
                logger.debug("前端接收COM_QUERY命令");
                MyByteBuff myByteBuff = (MyByteBuff) o;
                if (myByteBuff == null) {
                    myByteBuff = frontendMysqlConnection.read();
                }
                if (backendMysqlConnection == null) {
                    //TODO 根据sql从后端连接池中取出连接中取出连接
                    //TODO 如果没有则新建
                    logger.debug("收到COM_QUERY命令,准备创建后端连接，或者从连接池中取出连接？？");
                    backendMysqlConnection = BackendMysqlConnectionFactory.INSTANCE.create(Constants.MYSQL_SERVER_IP, Constants.MYSQL_SERVER_PORT);
                    backendMysqlConnection.setFrontendMysqlConnection(frontendMysqlConnection);
                    frontendMysqlConnection.setBackendMysqlConnection(backendMysqlConnection);
                    ServerContext.getInstance().getConnector().connect(backendMysqlConnection);
                    return;
                }
                if (frontendMysqlConnection.getDirectTransferPacketWriteLen() == 0) {
                    backendMysqlConnection.setWriteBuff(myByteBuff);
                }
                backendMysqlConnection.drive(null);
            }
        } catch (Throwable e) {
            //TODO 处理异常
            e.printStackTrace();
        }
    }
}
