
package com.mogujie.tt.task.biz;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.mogujie.tt.biz.MessageHelper;
import com.mogujie.tt.config.HandlerConstant;
import com.mogujie.tt.config.ProtocolConstant;
import com.mogujie.tt.config.TaskConstant;
import com.mogujie.tt.entity.MessageInfo;
import com.mogujie.tt.packet.PacketDistinguisher;
import com.mogujie.tt.packet.action.ActionCallback;
import com.mogujie.tt.packet.base.Packet;
import com.mogujie.tt.packet.biz.MessagePacket.SendMessageRequest;
import com.mogujie.tt.task.MAsyncTask;
import com.mogujie.tt.task.TaskManager;
import com.mogujie.tt.ui.activity.MessageActivity;

/**
 * @Description 发送语音消息Task
 * @author Nana
 * @date 2014-5-10
 */
public class SendAudioMessageTask extends MAsyncTask {
    MessageInfo message;
    String audioPath;
    int audioLen;
    Context context;

    public SendAudioMessageTask(Context cxt, MessageInfo msg, String path,
            int len) {
        message = msg;
        audioPath = path;
        audioLen = len;
        context = cxt;
    }

    @Override
    public int getTaskType() {
        return TaskConstant.TASK_SEND_AUDIO_MESSAGE;
    }

    @Override
    public Object doTask() {

        doSendTask(message);

        return true;
    }

    private void doSendTask(MessageInfo msgInfo) {

        Object[] objs = new Object[1];
        objs[0] = msgInfo;
        Packet packet = PacketDistinguisher.make(ProtocolConstant.SID_MSG,
                ProtocolConstant.CID_MSG_DATA, objs, true);
        ActionCallback callback = new ActionCallback() {

            public void onSuccess(Packet packet) {
                MessageHelper.sendMessageToMsgHandler(packet, MessageActivity.getMsgHandler());
            }

            @Override
            public void onTimeout(Packet packet) {

                if (null == packet)
                    return;

                SendMessageRequest request = (SendMessageRequest) packet
                        .getRequest();
                if (null == request)
                    return;

                MessageInfo info = request.getMsgInfo();
                if (null == info)
                    return;

                Handler handler = MessageActivity.getUiHandler();
                Message message = handler.obtainMessage();
                message.what = HandlerConstant.HANDLER_SEND_MESSAGE_TIMEOUT;
                message.obj = info;
                handler.sendMessage(message);
            }

            @Override
            public void onFaild(Packet packet) {
                onTimeout(packet);
            }
        };
        PushActionToQueueTask task = new PushActionToQueueTask(packet, callback);
        TaskManager.getInstance().trigger(task);
    }

}
