
package com.mogujie.tt.ui.activity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener2;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.mogujie.tt.R;
import com.mogujie.tt.adapter.MessageAdapter;
import com.mogujie.tt.adapter.album.AlbumHelper;
import com.mogujie.tt.adapter.album.ImageBucket;
import com.mogujie.tt.app.IMEntrance;
import com.mogujie.tt.audio.biz.AudioPlayerHandler;
import com.mogujie.tt.audio.biz.AudioRecordHandler;
import com.mogujie.tt.biz.MessageHelper;
import com.mogujie.tt.biz.MessageNotifyCenter;
import com.mogujie.tt.cache.biz.CacheHub;
import com.mogujie.tt.config.HandlerConstant;
import com.mogujie.tt.config.ProtocolConstant;
import com.mogujie.tt.config.SysConstant;
import com.mogujie.tt.conn.NetStateDispach;
import com.mogujie.tt.conn.ReconnectManager;
import com.mogujie.tt.conn.StateManager;
import com.mogujie.tt.entity.MessageInfo;
import com.mogujie.tt.entity.User;
import com.mogujie.tt.packet.MessageDispatchCenter;
import com.mogujie.tt.task.TaskCallback;
import com.mogujie.tt.task.TaskManager;
import com.mogujie.tt.task.biz.SendAudioMessageTask;
import com.mogujie.tt.task.biz.UploadImageTask;
import com.mogujie.tt.ui.base.TTBaseActivity;
import com.mogujie.tt.ui.tools.Emoparser;
import com.mogujie.tt.ui.tools.ImageTool;
import com.mogujie.tt.utils.CommonUtil;
import com.mogujie.tt.utils.FileUtil;
import com.mogujie.tt.widget.EmoGridView;
import com.mogujie.tt.widget.MGDialog;
import com.mogujie.tt.widget.MGProgressbar;
import com.mogujie.tt.widget.PinkToast;
import com.mogujie.tt.widget.SpeekerToast;
import com.mogujie.tt.widget.EmoGridView.OnEmoGridViewItemClick;
import com.mogujie.tt.widget.MGDialog.OnButtonClickListener;

/**
 * @Description 主消息界面
 * @author Nana
 * @date 2014-7-15
 */
public class MessageActivity extends TTBaseActivity implements
        OnRefreshListener2<ListView>, View.OnClickListener, OnTouchListener,
        TextWatcher, SensorEventListener {
    private static Handler uiHandler = null;// 处理界面消息
    private static Handler msgHandler = null;// 处理协议消息
    private PullToRefreshListView lvPTR = null;
    private EditText messageEdt = null;
    private TextView sendBtn = null;
    private Button recordAudioBtn = null;
    private ImageView keyboardInputImg = null;
    private ImageView soundVolumeImg = null;
    private LinearLayout soundVolumeLayout = null;
    private static MessageAdapter adapter = null;
    private ImageView audioInputImg = null;
    private ImageView addPhotoBtn = null;
    private ImageView addEmoBtn = null;
    private EmoGridView emoGridView = null;
    private String audioSavePath = null;
    private InputMethodManager inputManager = null;
    private boolean textChanged = false;
    private AudioRecordHandler audioRecorderInstance = null;
    private Thread audioRecorderThread = null;
    private Dialog soundVolumeDialog = null;
    private View unreadMessageNotifyView = null;
    private View addOthersPanelView = null;
    private AlbumHelper albumHelper = null;
    private static List<ImageBucket> albumList = null;
    MGProgressbar progressbar = null;
    private boolean audioReday = false;
    static private AudioManager audioManager = null;
    static private SensorManager sensorManager = null;
    static private Sensor sensor = null;
    static private int audioPlayMode = SysConstant.AUDIO_PLAY_MODE_NORMAL;
    private int preAudioPlayMode = SysConstant.AUDIO_PLAY_MODE_NORMAL;
    private String takePhotoSavePath = "";
    // 避免用户信息与商品详情的重复请求
    public static boolean requestingGoodsDetail = false;
    public static boolean requestingUserInfo = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        initData();
        initHandler();
        registEvents();
        initAudioSensor();
        IMEntrance.getInstance().setContext(MessageActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从联系人切换到消息界面时设置聊天界面信息
        fillMessageViewByContact(getIntent());
        // 检测是否是黑名单用户
        MessageHelper.blockUserCheck(CacheHub.getInstance().getChatUserId(), uiHandler);
        // 修改消息状态
        setMessageState();
        // 修改消息状态修改后，通知联系人列表更新列表信息
        MessageNotifyCenter.getInstance().doNotify(SysConstant.EVENT_RECENT_INFO_CHANGED);
        // 检测是否被踢下线
        checkKickOff();
        // 下面的标志用于防止用户信息与商品详情的重复请求
        MessageActivity.requestingGoodsDetail = false;
        MessageActivity.requestingUserInfo = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;

        // 拍照时如果断网直接提示，不进行图片消息的展示
        if (!StateManager.getInstance().isOnline()) {
            PinkToast.makeText(MessageActivity.this,
                    getResources().getString(R.string.disconnected_by_server),
                    Toast.LENGTH_LONG).show();

            return;
        }

        switch (requestCode) {
            case SysConstant.CAMERA_WITH_DATA:
                handleTakePhotoData(data);
                break;
            case SysConstant.ALBUM_BACK_DATA:
                setIntent(data);
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void initHandler() {
        msgHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case ProtocolConstant.SID_MSG * 1000
                            + ProtocolConstant.CID_MSG_DATA_ACK:
                        MessageHelper.onReceiveMsgACK(msg.obj);
                        break;
                    case ProtocolConstant.SID_MSG * 1000
                            + ProtocolConstant.CID_MSG_DATA:
                        MessageHelper.onReceiveMessage(msg.obj);
                        break;
                    case ProtocolConstant.SID_OTHER * 1000
                            + ProtocolConstant.CID_GET_USER_INFO_RESPONSE:
                        MessageHelper.onGetUserInfo(msg.obj, uiHandler, MessageActivity.this);
                        break;
                }
            }
        };

        uiHandler = new Handler() {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case HandlerConstant.HANDLER_RECORD_FINISHED:
                        onRecordVoiceEnd((Float) msg.obj);
                        break;
                    case HandlerConstant.HANDLER_NET_STATE_DISCONNECTED:
                        setTitle(getString(R.string.disconnected));
                        break;
                    case HandlerConstant.HANDLER_LOGIN_MSG_SERVER:
                        MessageHelper.onConnectedMsgServer(msgHandler, uiHandler);
                        break;
                    case HandlerConstant.HANDLER_IMAGE_UPLOAD_FAILD:
                        onUploadImageFaild(msg.obj);
                        break;
                    case HandlerConstant.HANDLER_IMAGE_UPLOAD_SUCESS:
                        MessageHelper.onImageUploadFinish(msg.obj, uiHandler, msgHandler);
                        break;
                    case HandlerConstant.HANDLER_LOGIN_KICK:
                        if (CommonUtil.isTopActivy(MessageActivity.this,
                                SysConstant.MESSAGE_ACTIVITY)) {
                            checkKickOff();
                        }
                        break;
                    case HandlerConstant.HANDLER_STOP_PLAY:
                        adapter.stopVoicePlayAnim((String) msg.obj);
                        break;
                    case HandlerConstant.RECEIVE_MAX_VOLUME:
                        onReceiveMaxVolume((Integer) msg.obj);
                        break;
                    case HandlerConstant.RECORD_AUDIO_TOO_LONG:
                        doFinishRecordAudio();
                        break;
                    case HandlerConstant.HANDLER_MESSAGES_NEW_MESSAGE_COME:
                        if (null != CacheHub.getInstance().getChatUser()
                                && CommonUtil.isTopActivy(MessageActivity.this,
                                        SysConstant.MESSAGE_ACTIVITY)) {
                            // 设置消息状态并通知联系人列表更新列表信息
                            setMessageState();
                            MessageNotifyCenter.getInstance().doNotify(
                                    SysConstant.EVENT_RECENT_INFO_CHANGED);
                        }
                        break;
                    case HandlerConstant.HANDLER_SEND_MESSAGE_TIMEOUT:
                        adapter.updateMessageState((MessageInfo) msg.obj,
                                SysConstant.MESSAGE_STATE_FINISH_FAILED);
                        break;
                    case HandlerConstant.HANDLER_SEND_MESSAGE_FAILED:
                        adapter.updateMessageState((MessageInfo) msg.obj,
                                SysConstant.MESSAGE_STATE_FINISH_FAILED);
                        break;
                    case HandlerConstant.SHOULD_BLOCK_USER:
                        blockUser(true);
                        break;
                    case HandlerConstant.SHOULD_NOT_BLOCK_USER:
                        blockUser(false);
                        break;
                    case HandlerConstant.START_BLOCK_CHECK:
                        onStartBlockCheck();
                        break;
                    case HandlerConstant.SET_TITLE:
                        enableBottomView(true);
                        setTitleByUser((User) msg.obj);
                        break;
                    case HandlerConstant.REQUEST_CUSTOM_SERVICE_FAILED:
                        onRequestCustomServiceFailed();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    /**
     * @Description 请求客服失败后处理
     */
    private void onRequestCustomServiceFailed() {
        enableBottomView(false);// 禁用输入框
        setTitle(R.string.request_custom_service_failed);// 标题栏给出失败提示
    }

    /**
     * @Description 是否禁用底部输入控件
     * @param enabled
     */
    private void enableBottomView(boolean enabled) {
        sendBtn.setEnabled(enabled);
        recordAudioBtn.setEnabled(enabled);
        audioInputImg.setEnabled(enabled);
        messageEdt.setEnabled(enabled);
        keyboardInputImg.setEnabled(enabled);
        addPhotoBtn.setEnabled(enabled);
        addEmoBtn.setEnabled(enabled);
    }

    /**
     * @Description 显示联系人界面
     */
    private void showGroupManageActivity(boolean fromMessagePage) {
        Intent i = new Intent(this, GroupManagermentActivity.class);
        startActivity(i);
    }

    /**
     * @Description 注册事件
     */
    private void registEvents() {
        // 接收未读消息提示
        MessageNotifyCenter.getInstance().register(
                SysConstant.EVENT_UNREAD_MSG, getUiHandler(),
                HandlerConstant.HANDLER_MESSAGES_NEW_MESSAGE_COME);

        // 接收网络状态通知
        NetStateDispach.getInstance().register(MessageActivity.class,
                uiHandler);
    }

    /**
     * @Description 取消事件注册
     */
    private void unregistEvents() {
        MessageDispatchCenter.getInstance().unRegister(uiHandler);
        MessageNotifyCenter.getInstance().unregister(
                SysConstant.EVENT_UNREAD_MSG, getUiHandler(),
                HandlerConstant.HANDLER_MESSAGES_NEW_MESSAGE_COME);
    }

    /**
     * @Description 初始化AudioManager，用于访问控制音量和钤声模式
     */
    private void initAudioSensor() {
        audioManager = (AudioManager) this
                .getSystemService(Context.AUDIO_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * @Description 初始化数据（相册,表情,数据库相关）
     */
    private void initData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                albumHelper = AlbumHelper.getHelper(MessageActivity.this);
                albumList = albumHelper.getImagesBucketList(false);
                Emoparser.getInstance(MessageActivity.this);
            }
        }).start();
    }

    /**
     * @Description 初始化界面控件
     */
    private void initView() {
        //设置顶部标题栏
        setLeftButton(R.drawable.tt_top_back);
        setLeftText(getResources().getString(R.string.top_left_back));
        topLeftBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                MessageActivity.this.finish();
            }
        });
        // 绑定布局资源(注意放所有资源初始化之前)
        LayoutInflater.from(this).inflate(R.layout.tt_activity_message,
                topContentView);
        // 右上角联系人图标
        setRightButton(R.drawable.tt_top_right_group_manager);

        // 未读消息提示
        unreadMessageNotifyView = new View(this);
        unreadMessageNotifyView
                .setBackgroundResource(R.drawable.tt_unread_message_notify_bg);
        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources()
                        .getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, width);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.topMargin = width - 4;
        lp.rightMargin = width - 5;
        unreadMessageNotifyView.setLayoutParams(lp);
        topBar.addView(unreadMessageNotifyView, lp);
        unreadMessageNotifyView.setVisibility(View.GONE);

        // 输入对象
        inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // 表情
        emoGridView = (EmoGridView) findViewById(R.id.emo_gridview);
        emoGridView.setOnEmoGridViewItemClick(new OnEmoGridViewItemClick() {
            @Override
            public void onItemClick(int facesPos, int viewIndex) {
                int deleteId = (++viewIndex) * (SysConstant.pageSize - 1);
                if (deleteId > Emoparser.getInstance(MessageActivity.this).getResIdList().length) {
                    deleteId = Emoparser.getInstance(MessageActivity.this).getResIdList().length;
                }
                if (deleteId == facesPos) {
                    String msgContent = messageEdt.getText().toString();
                    if (msgContent.isEmpty())
                        return;
                    if (msgContent.contains("["))
                        msgContent = msgContent.substring(0,
                                msgContent.lastIndexOf("["));
                    messageEdt.setText(msgContent);
                } else {
                    int resId = Emoparser.getInstance(MessageActivity.this)
                            .getResIdList()[facesPos];
                    String pharse = Emoparser.getInstance(MessageActivity.this)
                            .getIdPhraseMap().get(resId);
                    int startIndex = messageEdt.getSelectionStart();
                    Editable edit = messageEdt.getEditableText();
                    if (startIndex < 0 || startIndex >= edit.length()) {
                        if (null != pharse) {
                            edit.append(pharse);
                        }
                    } else {
                        if (null != pharse) {
                            edit.insert(startIndex, pharse);
                        }
                    }
                }
                Editable edtable = messageEdt.getText();
                int position = edtable.length();
                Selection.setSelection(edtable, position);
            }
        });
        emoGridView.setAdapter();

        // 列表控件(开源PTR)
        lvPTR = (PullToRefreshListView) this.findViewById(R.id.message_list);

        Drawable loadingDrawable = getResources().getDrawable(
                R.drawable.pull_to_refresh_indicator);
        final int indicatorWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 29, getResources()
                        .getDisplayMetrics());
        loadingDrawable
                .setBounds(new Rect(0, indicatorWidth, 0, indicatorWidth));
        lvPTR.getLoadingLayoutProxy().setLoadingDrawable(loadingDrawable);

        lvPTR.getRefreshableView().setCacheColorHint(Color.WHITE);
        lvPTR.getRefreshableView().setSelector(
                new ColorDrawable(Color.WHITE));
        lvPTR.getRefreshableView().setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            if (emoGridView.getVisibility() == View.VISIBLE) {
                                emoGridView.setVisibility(View.GONE);
                            }

                            if (addOthersPanelView.getVisibility() == View.VISIBLE) {
                                addOthersPanelView.setVisibility(View.GONE);
                            }
                            inputManager.hideSoftInputFromWindow(
                                    messageEdt.getWindowToken(), 0);
                        }
                        return false;
                    }
                });

        lvPTR.getRefreshableView().addHeaderView(
                LayoutInflater.from(this).inflate(
                        R.layout.tt_messagelist_header,
                        lvPTR.getRefreshableView(), false));
        adapter = new MessageAdapter(this);
        lvPTR.setAdapter(adapter);
        lvPTR.setOnRefreshListener(this);

        // 界面底部输入框布局
        sendBtn = (TextView) this.findViewById(R.id.send_message_btn);
        recordAudioBtn = (Button) this.findViewById(R.id.record_voice_btn);
        audioInputImg = (ImageView) this.findViewById(R.id.voice_btn);
        messageEdt = (EditText) this.findViewById(R.id.message_text);
        RelativeLayout.LayoutParams param = (LayoutParams) messageEdt
                .getLayoutParams();
        param.addRule(RelativeLayout.LEFT_OF, R.id.show_add_photo_btn);
        param.addRule(RelativeLayout.RIGHT_OF, R.id.show_emo_btn);
        messageEdt
                .setOnFocusChangeListener(new android.view.View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            scrollToBottomListItem();
                            if (emoGridView.getVisibility() == View.VISIBLE) {
                                emoGridView.setVisibility(View.GONE);
                            }

                            if (addOthersPanelView.getVisibility() == View.VISIBLE) {
                                addOthersPanelView.setVisibility(View.GONE);
                            }
                        }
                    }
                });
        messageEdt.setOnClickListener(this);

        keyboardInputImg = (ImageView) this.findViewById(R.id.show_keyboard_btn);
        addPhotoBtn = (ImageView) this.findViewById(R.id.show_add_photo_btn);
        addPhotoBtn.setOnClickListener(this);
        addEmoBtn = (ImageView) this.findViewById(R.id.show_emo_btn);
        addEmoBtn.setOnClickListener(this);
        initSoundVolumeDlg();

        addOthersPanelView = findViewById(R.id.add_others_panel);
        View takePhotoBtn = findViewById(R.id.take_photo_btn);
        takePhotoBtn.setOnClickListener(this);
        View takeCameraBtn = findViewById(R.id.take_camera_btn);
        takeCameraBtn.setOnClickListener(this);

        // 初始化滚动条(注意放到最后)
        View view = LayoutInflater.from(MessageActivity.this).inflate(
                R.layout.tt_progress_ly, null);
        progressbar = (MGProgressbar) view.findViewById(R.id.tt_progress);
        LayoutParams pgParms = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        pgParms.bottomMargin = 50;
        addContentView(view, pgParms);

        // 绑定各控件事件监听对象
        messageEdt.addTextChangedListener(this);
        keyboardInputImg.setOnClickListener(this);
        audioInputImg.setOnClickListener(this);
        recordAudioBtn.setOnTouchListener(this);
        sendBtn.setOnClickListener(this);
        topLeftBtn.setOnClickListener(this);
        topRightBtn.setOnClickListener(this);
    }

    /**
     * @Description 初始化音量对话框
     */
    private void initSoundVolumeDlg() {
        soundVolumeDialog = new Dialog(this, R.style.SoundVolumeStyle);
        soundVolumeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        soundVolumeDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        soundVolumeDialog.setContentView(R.layout.tt_sound_volume_dialog);
        soundVolumeDialog.setCanceledOnTouchOutside(true);
        soundVolumeImg = (ImageView) soundVolumeDialog
                .findViewById(R.id.sound_volume_img);
        soundVolumeLayout = (LinearLayout) soundVolumeDialog
                .findViewById(R.id.sound_volume_bk);
    }

    /**
     * @Description 判断如果是从联系人界面切换到消息界面，则根据需要渲染消息界面
     * @param intent
     */
    private void fillMessageViewByContact(Intent intent) {
        if (null == intent)
            return;
        Bundle bundle = intent.getExtras();
        // 设置消息界面的标题
        setTitleByUser(CacheHub.getInstance().getChatUser());

        // 加载已读聊天消息
        int readCount = bundle.getInt(SysConstant.READCOUNT, 0);
        User chatUser = CacheHub.getInstance().getChatUser();
        if (chatUser != null) {
            adapter.clearItem();
            adapter.clearPositionSEQNoMap();
            List<MessageInfo> historyMsgInfo = CacheHub.getInstance()
                    .pullMsg(CacheHub.getInstance().getLoginUserId(),
                            chatUser.getUserId(), 0,
                            readCount, SysConstant.HISTORY_PULL_PER_NUM);
            if (!historyMsgInfo.isEmpty()) {
                adapter.addHistoryDivideTag();
                // 处理一些特殊情况SDCARD不可用
                if (!FileUtil.isSdCardAvailuable()) {
                    for (MessageInfo info : historyMsgInfo) {
                        if (info.getDisplayType() == SysConstant.DISPLAY_TYPE_IMAGE) {
                            info.setMsgLoadState(SysConstant.MESSAGE_STATE_FINISH_FAILED);
                            CacheHub.getInstance()
                                    .updateMsgStatus(
                                            info.getMsgId(),
                                            SysConstant.MESSAGE_STATE_FINISH_FAILED);
                        }
                    }
                }
            }
            adapter.addItem(true, (ArrayList<MessageInfo>) historyMsgInfo);
            adapter.notifyDataSetChanged();

            // 加载未读消息
            List<MessageInfo> unReadMsgInfo = CacheHub.getInstance().pullMsg(
                    CacheHub.getInstance().getLoginUserId(),
                    chatUser.getUserId(), 0, 0, readCount);
            for (int i = 0; i < unReadMsgInfo.size(); i++) {
                adapter.addItem(unReadMsgInfo.get(i));
            }
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * @Description 设置会话对象设置消息标题
     * @param user
     */
    private void setTitleByUser(User user) {
        if (null == user) {
            return;
        }
        if (!TextUtils.isEmpty(user.getNickName())) {
            setTitle(user.getNickName());
        } else if (!TextUtils.isEmpty(user.getName())) {
            setTitle(user.getName());
        } else {
            setTitle(user.getUserId());
        }
    }

    /**
     * @Description 设置消息相关状态：未读消息计数，消息已读状态，界面未读消息提示
     */
    private void setMessageState() {
        User loginUser = CacheHub.getInstance().getLoginUser();
        User chatUser = CacheHub.getInstance().getChatUser();
        if (null != loginUser && null != chatUser) {
            CacheHub.getInstance().clearUnreadCount(chatUser.getUserId());
            CacheHub.getInstance().updateMsgReadStatus(loginUser.getUserId(),
                    chatUser.getUserId(), SysConstant.MESSAGE_ALREADY_READ);
        }
        if (0 < CacheHub.getInstance().getUnreadCount()) {
            unreadMessageNotifyView.setVisibility(View.VISIBLE);
        } else {
            unreadMessageNotifyView.setVisibility(View.GONE);
        }
    }

    public void showProgress() {
        progressbar.showProgress();
    }

    public void hideProgress() {
        progressbar.hideProgress();
    }

    /**
     * @Description 开始检测是否是黑名单用户时，界面显示状态
     */
    private void onStartBlockCheck() {
        showProgress();
        enableBottomView(false);
    }

    /**
     * @Description 根据是否是黑名单用户显示相关信息
     * @param block
     */
    private void blockUser(boolean block) {
        hideProgress();
        if (block) {
            PinkToast.makeText(MessageActivity.this, getString(R.string.block_chat),
                    Toast.LENGTH_LONG).show();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    MessageActivity.this.finish();
                }
            }, 1000);
        } else {
            enableBottomView(true);
        }
    }

    /**
     * @Description 修改消息的文件保存路径
     * @param msgId
     * @param path
     */
    public static void updateMessageSavePath(int msgId, String path) {
        adapter.updateItemSavePath(msgId, path);
    }

    /**
     * @Description 修改消息状态
     */
    public static void updateMessageState(int msgId, int state) {
        adapter.updateItemState(msgId, state);
    }

    /**
     * @Description 修改消息状态
     */
    public static void updateMessageState(MessageInfo msgInfo, int state) {
        adapter.updateMessageState(msgInfo, state);
    }

    /**
     * @Description 清空适配器
     */
    public static void clearItem() {
        adapter.clearItem();
        adapter.notifyDataSetChanged();
    }

    /**
     * @Description 向消息列表适配器中添加一条消息
     * @param msgInfo
     */
    public static void addItem(MessageInfo msgInfo) {
        adapter.addItem(msgInfo);
        adapter.notifyDataSetChanged();
    }

    /**
     * @Description 向消息列表适配器中添加历史消息
     * @param fromStart
     * @param historyMsgInfo
     */
    public static void addItem(boolean fromStart, ArrayList<MessageInfo> historyMsgInfo) {
        adapter.addItem(true, historyMsgInfo);
        adapter.notifyDataSetChanged();
    }

    /**
     * @Description 录音超时(60s)，发消息调用该方法
     */
    public void doFinishRecordAudio() {
        try {
            if (audioRecorderInstance.isRecording()) {
                audioRecorderInstance.setRecording(false);
            }
            if (soundVolumeDialog.isShowing()) {
                soundVolumeDialog.dismiss();
            }

            recordAudioBtn
                    .setBackgroundResource(R.drawable.tt_pannel_btn_voiceforward_normal);

            audioRecorderInstance.setRecordTime(SysConstant.MAX_SOUND_RECORD_TIME);
            onRecordVoiceEnd(SysConstant.MAX_SOUND_RECORD_TIME);
        } catch (Exception e) {
        }
    }

    /**
     * @Description 根据分贝值设置录音时的音量动画
     * @param voiceValue
     */
    private void onReceiveMaxVolume(int voiceValue) {
        if (voiceValue < 200.0) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_01);
        } else if (voiceValue > 200.0 && voiceValue < 600) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_02);
        } else if (voiceValue > 600.0 && voiceValue < 1200) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_03);
        } else if (voiceValue > 1200.0 && voiceValue < 2400) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_04);
        } else if (voiceValue > 2400.0 && voiceValue < 10000) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_05);
        } else if (voiceValue > 10000.0 && voiceValue < 28000.0) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_06);
        } else if (voiceValue > 28000.0) {
            soundVolumeImg.setImageResource(R.drawable.tt_sound_volume_07);
        }
    }

    /**
     * @Description 图片上传失败时界面的相关显示
     * @param obj
     */
    private void onUploadImageFaild(Object obj) {
        if (obj == null)
            return;
        MessageInfo messageInfo = (MessageInfo) obj;
        adapter.updateMessageState(messageInfo, SysConstant.MESSAGE_STATE_FINISH_FAILED);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    /**
     * @Description 处理拍照后的数据
     * @param data
     */
    private void handleTakePhotoData(Intent data) {
        Bitmap bitmap = null;
        if (data == null) {
            bitmap = ImageTool.createImageThumbnail(takePhotoSavePath);
        } else {
            Bundle extras = data.getExtras();
            bitmap = extras == null ? null : (Bitmap) extras.get("data");
        }
        if (bitmap == null) {
            return;
        }
        // 将图片发送至服务器
        MessageInfo msg = new MessageInfo();
        msg.setMsgFromUserId(CacheHub.getInstance().getLoginUserId());
        msg.setIsSend(true);
        msg.setMsgCreateTime((int) (System.currentTimeMillis() / 1000));
        msg.setSavePath(takePhotoSavePath);
        msg.setDisplayType(SysConstant.DISPLAY_TYPE_IMAGE);
        msg.setMsgType(SysConstant.MESSAGE_TYPE_TELETEXT);
        msg.setMsgContent("");
        msg.setTargetId(CacheHub.getInstance().getChatUserId());
        msg.setMsgReadStatus(SysConstant.MESSAGE_ALREADY_READ);
        msg.setMsgLoadState(SysConstant.MESSAGE_STATE_LOADDING);
        int messageSendRequestNo = CacheHub.getInstance().obtainMsgId();
        msg.setMsgId(messageSendRequestNo);
        CacheHub.getInstance().pushMsg(msg);

        addItem(msg);

        List<MessageInfo> messageList = new ArrayList<MessageInfo>();
        messageList.add(msg);
        String Dao ="";// TokenManager.getInstance().getDao();
        UploadImageTask upTask = new UploadImageTask(
                SysConstant.UPLOAD_IMAGE_HOST, Dao, messageList);
        TaskManager.getInstance().trigger(upTask);
    }

    /**
     * @Description 录音结束后处理录音数据
     * @param audioLen
     */
    private void onRecordVoiceEnd(float audioLen) {
        User chatUser = CacheHub.getInstance().getChatUser();
        if (chatUser == null) {
            PinkToast.makeText(MessageActivity.this,
                    getResources().getString(R.string.link_is_connecting),
                    Toast.LENGTH_LONG).show();

            return;
        }
        // 语音时长
        int tLen = (int) (audioLen + 0.5);
        tLen = tLen < 1 ? 1 : tLen;
        if (tLen < audioLen) {
            ++tLen;
        }

        // 绘制到界面（不关心语音内容）
        MessageInfo msg = new MessageInfo();
        msg.setDisplayType(SysConstant.DISPLAY_TYPE_AUDIO);
        msg.setMsgFromUserId(CacheHub.getInstance().getLoginUserId());
        msg.setIsSend(true);
        msg.setTargetId(chatUser.getUserId());
        msg.setMsgCreateTime((int) (System.currentTimeMillis() / 1000));
        msg.setMsgLoadState(SysConstant.MESSAGE_STATE_LOADDING);
        msg.setSavePath(audioSavePath);
        msg.setMsgContent("");
        msg.setMsgAttachContent("");
        msg.setMsgReadStatus(SysConstant.MESSAGE_ALREADY_READ);
        msg.setPlayTime(tLen);
        MessageHelper.setMsgAudioContent(msg);

        byte msgType = SysConstant.MESSAGE_TYPE_AUDIO;
        msg.setMsgType(msgType);

        int messageSendRequestNo = CacheHub.getInstance().obtainMsgId();
        msg.setMsgId(messageSendRequestNo);
        CacheHub.getInstance().pushMsg(msg);

        addItem(msg);

        // 设置语音内容,发送
        if (StateManager.getInstance().isOnline()) {
            SendAudioMessageTask sendTask = new SendAudioMessageTask(MessageActivity.this,
                    msg, audioSavePath, tLen);
            sendTask.setCallBack(new TaskCallback() {
                @Override
                public void callback(Object result) {
                    if (result == null) {
                        PinkToast.makeText(
                                MessageActivity.this,
                                getResources().getString(
                                        R.string.write_audio_file_failed),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
            TaskManager.getInstance().trigger(sendTask);
        } else {
            PinkToast.makeText(MessageActivity.this,
                    getResources().getString(R.string.disconnected_by_server),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        unregistEvents();
        adapter.clearItem();
        super.onDestroy();
    }

    @Override
    public void onPullUpToRefresh(PullToRefreshBase<ListView> refreshView) {
    }

    @Override
    public void onPullDownToRefresh(
            final PullToRefreshBase<ListView> refreshView) {
        // 获取消息
        refreshView.postDelayed(new Runnable() {
            @Override
            public void run() {
                User chatUser = CacheHub.getInstance().getChatUser();
                if (chatUser == null
                        || CacheHub.getInstance().getLoginUser() == null) {
                    return;
                }
                MessageInfo msgInfo = null;
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i) instanceof MessageInfo) {
                        msgInfo = (MessageInfo) adapter.getItem(i);
                        break;
                    }
                }
                int startMsgId = (null == msgInfo) ? 0 : msgInfo.getMsgId(); // 如果为空，则从最近一条消息拉取
                List<MessageInfo> historyMsgInfo = CacheHub
                        .getInstance()
                        .pullMsg(
                                CacheHub.getInstance().getLoginUserId(),
                                chatUser.getUserId(), startMsgId, 0,
                                SysConstant.HISTORY_PULL_PER_NUM
                                        + CacheHub.getInstance().getUnreadCount(
                                                chatUser.getUserId()));
                adapter.addItem(true, (ArrayList<MessageInfo>) historyMsgInfo);
                adapter.notifyDataSetChanged();
                ListView mlist = lvPTR.getRefreshableView();
                if (!(mlist).isStackFromBottom()) {
                    mlist.setStackFromBottom(true);
                }
                mlist.setStackFromBottom(false);
                refreshView.onRefreshComplete();
            }
        }, 200);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.left_btn) {
            MessageActivity.this.finish();
        } else if (id == R.id.right_btn) {
            showGroupManageActivity(true);
        } else if (id == R.id.show_add_photo_btn) {
            scrollToBottomListItem();
            if (addOthersPanelView.getVisibility() == View.VISIBLE) {
                addOthersPanelView.setVisibility(View.GONE);
            } else if (addOthersPanelView.getVisibility() == View.GONE) {
                addOthersPanelView.setVisibility(View.VISIBLE);
                inputManager.hideSoftInputFromWindow(
                        messageEdt.getWindowToken(), 0);
            }
            if (null != emoGridView && emoGridView.getVisibility() == View.VISIBLE) {
                emoGridView.setVisibility(View.GONE);
            }
        } else if (id == R.id.take_photo_btn) {
            scrollToBottomListItem();
            if (albumList.size() < 1) {
                PinkToast.makeText(MessageActivity.this,
                        getResources().getString(R.string.not_found_album),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(MessageActivity.this,
                    PickPhotoActivity.class);
            intent.putExtra(SysConstant.EXTRA_CHAT_USER_ID, CacheHub
                    .getInstance().getChatUserId());
            startActivityForResult(intent, SysConstant.ALBUM_BACK_DATA);
            MessageActivity.this.overridePendingTransition(
                    R.anim.tt_album_enter, R.anim.tt_stay);

            addOthersPanelView.setVisibility(View.GONE);
        } else if (id == R.id.take_camera_btn) {
            scrollToBottomListItem();
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePhotoSavePath = CommonUtil.getImageSavePath(String
                    .valueOf(System.currentTimeMillis()) + ".jpg");
            intent.putExtra(MediaStore.EXTRA_OUTPUT,
                    Uri.fromFile(new File(takePhotoSavePath)));
            startActivityForResult(intent, SysConstant.CAMERA_WITH_DATA);
            addOthersPanelView.setVisibility(View.GONE);
        } else if (id == R.id.show_emo_btn) {
            scrollToBottomListItem();

            inputManager.hideSoftInputFromWindow(
                    messageEdt.getWindowToken(), 0);
            if (emoGridView.getVisibility() == View.GONE) {
                emoGridView.setVisibility(View.VISIBLE);
            } else if (emoGridView.getVisibility() == View.VISIBLE) {
                emoGridView.setVisibility(View.GONE);
            }

            if (addOthersPanelView.getVisibility() == View.VISIBLE) {
                addOthersPanelView.setVisibility(View.GONE);
            }
        } else if (id == R.id.send_message_btn) {
            scrollToBottomListItem();
            if (!StateManager.getInstance().isOnline()) {
                PinkToast.makeText(
                        MessageActivity.this,
                        getResources().getString(
                                R.string.disconnected_by_server),
                        Toast.LENGTH_LONG).show();

                return;
            }
            User chatUser = CacheHub.getInstance().getChatUser();
            if (chatUser == null) {
                PinkToast.makeText(MessageActivity.this,
                        getResources().getString(R.string.link_is_connecting),
                        Toast.LENGTH_LONG).show();
                return;
            }

            String content = messageEdt.getText().toString();
            if (content.trim().equals("")) {
                PinkToast.makeText(MessageActivity.this,
                        getResources().getString(R.string.message_null),
                        Toast.LENGTH_LONG).show();
                return;
            }
            MessageInfo msg = MessageHelper.obtainTextMessage(chatUser.getUserId(), content);
            if (msg != null) {
                addItem(msg);
                MessageHelper.doSendTask(msg, uiHandler, msgHandler);
                messageEdt.setText("");
                // emoGridView.setVisibility(View.GONE);
            }

        } else if (id == R.id.voice_btn) {
            inputManager
                    .hideSoftInputFromWindow(messageEdt.getWindowToken(), 0);
            messageEdt.setVisibility(View.GONE);
            audioInputImg.setVisibility(View.GONE);
            recordAudioBtn.setVisibility(View.VISIBLE);
            keyboardInputImg.setVisibility(View.VISIBLE);
            emoGridView.setVisibility(View.GONE);
            addOthersPanelView.setVisibility(View.GONE);
            addEmoBtn.setVisibility(View.GONE);
            messageEdt.setText("");
        } else if (id == R.id.show_keyboard_btn) {
            recordAudioBtn.setVisibility(View.GONE);
            keyboardInputImg.setVisibility(View.GONE);
            messageEdt.setVisibility(View.VISIBLE);
            audioInputImg.setVisibility(View.VISIBLE);
            addEmoBtn.setVisibility(View.VISIBLE);
        } else if (id == R.id.message_text) {
            if (addOthersPanelView.getVisibility() == View.VISIBLE) {
                addOthersPanelView.setVisibility(View.GONE);
            }
            emoGridView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int id = v.getId();
        scrollToBottomListItem();
        if (id == R.id.record_voice_btn) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!StateManager.getInstance().isOnline()) {
                    PinkToast.makeText(
                            MessageActivity.this,
                            getResources().getString(
                                    R.string.disconnected_by_server),
                            Toast.LENGTH_LONG).show();

                    return false;
                }

                if (AudioPlayerHandler.getInstance().isPlaying())
                    AudioPlayerHandler.getInstance().stopPlayer();
                y1 = event.getY();
                recordAudioBtn
                        .setBackgroundResource(R.drawable.tt_pannel_btn_voiceforward_pressed);
                recordAudioBtn.setText(MessageActivity.this.getResources().getString(
                        R.string.release_to_send_voice));

                soundVolumeImg
                        .setImageResource(R.drawable.tt_sound_volume_01);
                soundVolumeImg.setVisibility(View.VISIBLE);
                soundVolumeLayout
                        .setBackgroundResource(R.drawable.tt_sound_volume_default_bk);
                soundVolumeDialog.show();
                audioSavePath = CommonUtil.getAudioSavePath(CacheHub
                        .getInstance().getLoginUserId());
                audioRecorderInstance = new AudioRecordHandler(audioSavePath,
                        new TaskCallback() {

                            @Override
                            public void callback(Object result) {
                                if (audioReday) {
                                    if (msgHandler != null) {
                                        Message msg = uiHandler
                                                .obtainMessage();
                                        msg.what = HandlerConstant.HANDLER_RECORD_FINISHED;
                                        msg.obj = audioRecorderInstance
                                                .getRecordTime();
                                        uiHandler.sendMessage(msg);
                                    }
                                }
                            }
                        });
                audioRecorderThread = new Thread(audioRecorderInstance);
                audioReday = false;
                audioRecorderInstance.setRecording(true);
                audioRecorderThread.start();
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                y2 = event.getY();
                if (y1 - y2 > 50) {
                    soundVolumeImg.setVisibility(View.GONE);
                    soundVolumeLayout
                            .setBackgroundResource(R.drawable.tt_sound_volume_cancel_bk);
                } else {
                    soundVolumeImg.setVisibility(View.VISIBLE);
                    soundVolumeLayout
                            .setBackgroundResource(R.drawable.tt_sound_volume_default_bk);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {

                if (!StateManager.getInstance().isOnline()) {
                    PinkToast.makeText(
                            MessageActivity.this,
                            getResources().getString(
                                    R.string.disconnected_by_server),
                            Toast.LENGTH_LONG).show();
                    if (soundVolumeDialog.isShowing()) {
                        soundVolumeDialog.dismiss();
                    }
                    return false;
                }
                if (audioRecorderInstance.isRecording()) {
                    audioRecorderInstance.setRecording(false);
                }
                if (soundVolumeDialog.isShowing()) {
                    soundVolumeDialog.dismiss();
                }
                recordAudioBtn
                        .setBackgroundResource(R.drawable.tt_pannel_btn_voiceforward_normal);
                recordAudioBtn.setText(MessageActivity.this.getResources().getString(
                        R.string.tip_for_voice_forward));
                if (y1 - y2 <= 50) {
                    if (audioRecorderInstance.getRecordTime() >= 0.5) {
                        if (audioRecorderInstance.getRecordTime() < SysConstant.MAX_SOUND_RECORD_TIME) {
                            audioReday = true;
                        }
                    } else {
                        soundVolumeImg.setVisibility(View.GONE);
                        soundVolumeLayout
                                .setBackgroundResource(R.drawable.tt_sound_volume_short_tip_bk);
                        soundVolumeDialog.show();
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            public void run() {
                                if (soundVolumeDialog.isShowing())
                                    soundVolumeDialog.dismiss();
                                this.cancel();
                            }
                        }, 700);
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onStop() {
        if (null != adapter) {
            adapter.hidePopup();
        }
        if (AudioPlayerHandler.getInstance().isPlaying())
            AudioPlayerHandler.getInstance().stopPlayer();
        super.onStop();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        scrollToBottomListItem();
        if (s.length() > 0) {
            String strMsg = messageEdt.getText().toString();
            CharSequence emoCharSeq = Emoparser.getInstance(
                    MessageActivity.this).emoCharsequence(strMsg);
            if (!textChanged) {
                textChanged = true;
                messageEdt.setText(emoCharSeq);
                Editable edtable = messageEdt.getText();
                int position = edtable.length();
                Selection.setSelection(edtable, position);

            } else {
                textChanged = false;
            }
            sendBtn.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams param = (LayoutParams) messageEdt
                    .getLayoutParams();
            param.addRule(RelativeLayout.LEFT_OF, R.id.send_message_btn);
            addPhotoBtn.setVisibility(View.GONE);
        } else {
            addPhotoBtn.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams param = (LayoutParams) messageEdt
                    .getLayoutParams();
            param.addRule(RelativeLayout.LEFT_OF, R.id.show_add_photo_btn);
            sendBtn.setVisibility(View.GONE);
        }
    }

    /**
     * @Description 滑动到列表底部
     */
    private void scrollToBottomListItem() {
        ListView lv = lvPTR.getRefreshableView();
        if (lv != null) {
            lv.setSelection(adapter.getCount() - 1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }

    @Override
    public void onSensorChanged(SensorEvent arg0) {
        if (!adapter.isAudioPlaying()) {
            return;
        }
        float range = arg0.values[0];
        if (range == sensor.getMaximumRange()) {
            if (audioPlayMode == AudioManager.MODE_NORMAL) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                if (preAudioPlayMode == AudioManager.MODE_IN_CALL) {
                    SpeekerToast.show(MessageActivity.this,
                            MessageActivity.this.getText(R.string.audio_in_speeker),
                            Toast.LENGTH_SHORT);
                }

                preAudioPlayMode = AudioManager.MODE_NORMAL;
            }
        } else {
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            SpeekerToast.show(MessageActivity.this,
                    MessageActivity.this.getText(R.string.audio_in_call),
                    Toast.LENGTH_SHORT);

            preAudioPlayMode = AudioManager.MODE_IN_CALL;
        }
    }

    /**
     * @Description 设置听筒模式
     * @param mode
     */
    public static void setAudioMode(int mode) {
        if (mode != SysConstant.AUDIO_PLAY_MODE_NORMAL
                && mode != SysConstant.AUDIO_PLAY_MODE_IN_CALL) {
            return;
        }
        audioPlayMode = mode;
        audioManager.setMode(audioPlayMode);
    }

    public static int getAudioMode() {
        return audioPlayMode;
    }

    /**
     * @Description 检查是否被踢下线
     * @return
     */
    private boolean checkKickOff() {
        if (ReconnectManager.getInstance().isKickOff()) {
            MGDialog.DialogBuilder builder = new MGDialog.DialogBuilder(
                    MessageActivity.this);
            builder.setTitleText(getString(R.string.kick_off)).setPositiveButtonText(
                    getString(R.string.reconnect));
            MGDialog dialog = builder.build();
            dialog.setOnButtonClickListener(new OnButtonClickListener() {

                @Override
                public void onOKButtonClick(MGDialog dialog) {
                    ReconnectManager.getInstance().setKickOff(false);
                    dialog.dismiss();
                }

                @Override
                public void onCancelButtonClick(MGDialog dialog) {

                }
            });
            dialog.show();
            return true;
        } else {
            return false;
        }
    }

    public static Handler getUiHandler() {
        return uiHandler;
    }

    public static Handler getMsgHandler() {
        return msgHandler;
    }

}
