import React, { useState, useEffect, useRef } from 'react';
import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously, signInWithCustomToken, onAuthStateChanged } from 'firebase/auth';
import { getFirestore, collection, addDoc, query, orderBy, onSnapshot, serverTimestamp, doc, updateDoc, arrayUnion, arrayRemove, getDocs, where } from 'firebase/firestore';

// Hàm tiện ích để cuộn xuống cuối tin nhắn
const scrollToBottom = (elementRef) => {
  if (elementRef.current) {
    elementRef.current.scrollTop = elementRef.current.scrollHeight;
  }
};

function App() {
  const [db, setDb] = useState(null);
  const [auth, setAuth] = useState(null);
  const [userId, setUserId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [isAuthReady, setIsAuthReady] = useState(false);
  const messagesEndRef = useRef(null);

  // State cho quản lý bạn bè
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [usersStatus, setUsersStatus] = useState({}); // Lưu trữ trạng thái online/offline của tất cả người dùng
  const [currentUserData, setCurrentUserData] = useState(null); // Dữ liệu của người dùng hiện tại
  const [showFriendManager, setShowFriendManager] = useState(false); // Hiển thị/ẩn trình quản lý bạn bè

  // Khởi tạo Firebase và đăng nhập
  useEffect(() => {
    const firebaseConfig = typeof __firebase_config !== 'undefined' ? JSON.parse(__firebase_config) : {};
    const appId = typeof __app_id !== 'undefined' ? __app_id : 'default-app-id';
    const initialAuthToken = typeof __initial_auth_token !== 'undefined' ? __initial_auth_token : null;

    const app = initializeApp(firebaseConfig);
    const firestore = getFirestore(app);
    const firebaseAuth = getAuth(app);

    setDb(firestore);
    setAuth(firebaseAuth);

    const unsubscribeAuth = onAuthStateChanged(firebaseAuth, async (user) => {
      if (user) {
        setUserId(user.uid);
        // Cập nhật trạng thái người dùng khi đăng nhập
        const userRef = doc(firestore, `artifacts/${appId}/public/data/users`, user.uid);
        await updateDoc(userRef, {
          status: 'online',
          lastSeen: serverTimestamp(),
        }, { merge: true }); // Sử dụng merge để không ghi đè các trường khác
      } else {
        try {
          if (initialAuthToken) {
            await signInWithCustomToken(firebaseAuth, initialAuthToken);
          } else {
            await signInAnonymously(firebaseAuth);
          }
        } catch (error) {
          console.error("Lỗi đăng nhập Firebase:", error);
        }
      }
      setIsAuthReady(true);
    });

    // Lắng nghe trạng thái người dùng (online/offline)
    const usersCollectionRef = collection(firestore, `artifacts/${appId}/public/data/users`);
    const unsubscribeUsers = onSnapshot(usersCollectionRef, (snapshot) => {
      const newUsersStatus = {};
      snapshot.docs.forEach(d => {
        newUsersStatus[d.id] = d.data();
      });
      setUsersStatus(newUsersStatus);
    }, (error) => {
      console.error("Lỗi khi lấy trạng thái người dùng:", error);
    });

    // Dọn dẹp listener khi component unmount
    return () => {
      unsubscribeAuth();
      unsubscribeUsers();
      // Đặt trạng thái người dùng thành offline khi thoát ứng dụng
      if (firebaseAuth.currentUser && firestore) {
        const userRef = doc(firestore, `artifacts/${appId}/public/data/users`, firebaseAuth.currentUser.uid);
        updateDoc(userRef, {
          status: 'offline',
          lastSeen: serverTimestamp(),
        }).catch(e => console.error("Lỗi khi cập nhật trạng thái offline:", e));
      }
    };
  }, []);

  // Lắng nghe tin nhắn theo thời gian thực
  useEffect(() => {
    if (db && isAuthReady && userId) {
      const messagesCollectionRef = collection(db, `artifacts/${__app_id}/public/data/messages`);
      const q = query(messagesCollectionRef, orderBy('timestamp'));

      const unsubscribe = onSnapshot(q, (snapshot) => {
        const newMessages = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data()
        }));
        setMessages(newMessages);
        scrollToBottom(messagesEndRef);
      }, (error) => {
        console.error("Lỗi khi lấy tin nhắn:", error);
      });

      return () => unsubscribe();
    }
  }, [db, isAuthReady, userId]);

  // Lắng nghe dữ liệu của người dùng hiện tại
  useEffect(() => {
    if (db && userId) {
      const userDocRef = doc(db, `artifacts/${__app_id}/public/data/users`, userId);
      const unsubscribe = onSnapshot(userDocRef, (docSnap) => {
        if (docSnap.exists()) {
          setCurrentUserData(docSnap.data());
        } else {
          // Tạo tài liệu người dùng nếu chưa tồn tại
          setDoc(userDocRef, {
            userId: userId,
            status: 'online',
            lastSeen: serverTimestamp(),
            friends: [],
            sentRequests: [],
            receivedRequests: [],
          }).catch(e => console.error("Lỗi khi tạo tài liệu người dùng:", e));
        }
      }, (error) => {
        console.error("Lỗi khi lấy dữ liệu người dùng hiện tại:", error);
      });

      return () => unsubscribe();
    }
  }, [db, userId]);

  // Xử lý gửi tin nhắn
  const handleSendMessage = async (type = 'message') => {
    if (db && userId && newMessage.trim() !== '') {
      try {
        await addDoc(collection(db, `artifacts/${__app_id}/public/data/messages`), {
          senderId: userId,
          text: newMessage.trim(),
          timestamp: serverTimestamp(),
          type: type,
          likes: [],
        });
        setNewMessage('');
        scrollToBottom(messagesEndRef);
      } catch (e) {
        console.error("Lỗi khi thêm tài liệu: ", e);
      }
    }
  };

  // Xử lý mô phỏng cuộc gọi
  const handleCall = () => {
    handleSendMessage('call');
  };

  // Xử lý thích/bỏ thích tin nhắn
  const handleLikeMessage = async (messageId, currentLikes) => {
    if (!db || !userId) return;

    const messageRef = doc(db, `artifacts/${__app_id}/public/data/messages`, messageId);
    const hasLiked = currentLikes.includes(userId);

    try {
      if (hasLiked) {
        await updateDoc(messageRef, {
          likes: arrayRemove(userId)
        });
      } else {
        await updateDoc(messageRef, {
          likes: arrayUnion(userId)
        });
      }
    } catch (e) {
      console.error("Lỗi khi cập nhật trạng thái thích: ", e);
    }
  };

  // Tìm kiếm người dùng
  const handleSearchUsers = async () => {
    if (!db || !searchTerm.trim()) {
      setSearchResults([]);
      return;
    }

    const usersCollectionRef = collection(db, `artifacts/${__app_id}/public/data/users`);
    const q = query(usersCollectionRef, where('userId', '>=', searchTerm), where('userId', '<=', searchTerm + '\uf8ff'));

    try {
      const querySnapshot = await getDocs(q);
      const results = querySnapshot.docs
        .map(d => d.data())
        .filter(user => user.userId !== userId); // Không hiển thị chính người dùng hiện tại
      setSearchResults(results);
    } catch (e) {
      console.error("Lỗi khi tìm kiếm người dùng: ", e);
      setSearchResults([]);
    }
  };

  // Gửi lời mời kết bạn
  const handleSendFriendRequest = async (targetUserId) => {
    if (!db || !userId || !currentUserData) return;

    const currentUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, userId);
    const targetUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, targetUserId);

    try {
      // Cập nhật người gửi: thêm vào sentRequests
      await updateDoc(currentUserRef, {
        sentRequests: arrayUnion(targetUserId)
      });
      // Cập nhật người nhận: thêm vào receivedRequests
      await updateDoc(targetUserRef, {
        receivedRequests: arrayUnion(userId)
      });
      console.log(`Đã gửi lời mời kết bạn đến ${targetUserId}`);
    } catch (e) {
      console.error("Lỗi khi gửi lời mời kết bạn: ", e);
    }
  };

  // Chấp nhận lời mời kết bạn
  const handleAcceptFriendRequest = async (requesterId) => {
    if (!db || !userId || !currentUserData) return;

    const currentUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, userId);
    const requesterUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, requesterId);

    try {
      // Cập nhật người dùng hiện tại: thêm vào friends, xóa khỏi receivedRequests
      await updateDoc(currentUserRef, {
        friends: arrayUnion(requesterId),
        receivedRequests: arrayRemove(requesterId)
      });
      // Cập nhật người gửi yêu cầu: thêm vào friends, xóa khỏi sentRequests
      await updateDoc(requesterUserRef, {
        friends: arrayUnion(userId),
        sentRequests: arrayRemove(userId)
      });
      console.log(`Đã chấp nhận lời mời kết bạn từ ${requesterId}`);
    } catch (e) {
      console.error("Lỗi khi chấp nhận lời mời kết bạn: ", e);
    }
  };

  // Từ chối lời mời kết bạn
  const handleRejectFriendRequest = async (requesterId) => {
    if (!db || !userId || !currentUserData) return;

    const currentUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, userId);
    const requesterUserRef = doc(db, `artifacts/${__app_id}/public/data/users`, requesterId);

    try {
      // Cập nhật người dùng hiện tại: xóa khỏi receivedRequests
      await updateDoc(currentUserRef, {
        receivedRequests: arrayRemove(requesterId)
      });
      // Cập nhật người gửi yêu cầu: xóa khỏi sentRequests
      await updateDoc(requesterUserRef, {
        sentRequests: arrayRemove(userId)
      });
      console.log(`Đã từ chối lời mời kết bạn từ ${requesterId}`);
    } catch (e) {
      console.error("Lỗi khi từ chối lời mời kết bạn: ", e);
    }
  };

  if (!isAuthReady || !currentUserData) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-100">
        <div className="text-lg font-semibold text-gray-700">Đang tải ứng dụng...</div>
      </div>
    );
  }

  // Hàm để lấy trạng thái của người dùng
  const getUserStatus = (id) => {
    const user = usersStatus[id];
    if (!user) return { status: 'unknown', color: 'text-gray-400' };

    const lastSeenTime = user.lastSeen ? user.lastSeen.toDate() : null;
    const now = new Date();
    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000); // 5 phút trước

    if (user.status === 'online' && lastSeenTime && lastSeenTime > fiveMinutesAgo) {
      return { status: 'online', color: 'text-green-500' };
    } else {
      return { status: 'offline', color: 'text-red-500' };
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gray-100 font-inter">
      {/* Header */}
      <header className="bg-gradient-to-r from-blue-500 to-indigo-600 p-4 text-white shadow-lg rounded-b-lg">
        <h1 className="text-3xl font-bold text-center">Ứng dụng Nhắn tin & Gọi điện</h1>
        <p className="text-center text-sm mt-1">ID người dùng của bạn: <span className="font-mono text-yellow-200">{userId}</span></p>
        <div className="flex justify-center mt-2">
          <button
            onClick={() => setShowFriendManager(!showFriendManager)}
            className="bg-white text-blue-600 px-4 py-2 rounded-full shadow-md hover:bg-blue-50 transition duration-300 ease-in-out"
          >
            {showFriendManager ? 'Ẩn quản lý bạn bè' : 'Hiển thị quản lý bạn bè'}
          </button>
        </div>
      </header>

      {/* Friend Manager Section */}
      {showFriendManager && (
        <div className="bg-white mx-4 mt-4 p-4 rounded-lg shadow-lg">
          <h2 className="text-xl font-bold mb-3 text-gray-800">Quản lý bạn bè</h2>

          {/* Tìm kiếm người dùng */}
          <div className="mb-4">
            <h3 className="text-lg font-semibold mb-2 text-gray-700">Tìm kiếm người dùng</h3>
            <div className="flex space-x-2">
              <input
                type="text"
                className="flex-1 p-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-400"
                placeholder="Nhập ID người dùng để tìm kiếm..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={(e) => {
                  if (e.key === 'Enter') {
                    handleSearchUsers();
                  }
                }}
              />
              <button
                onClick={handleSearchUsers}
                className="bg-purple-500 hover:bg-purple-600 text-white px-4 py-2 rounded-lg shadow-md transition duration-300"
              >
                Tìm kiếm
              </button>
            </div>
            {searchResults.length > 0 && (
              <div className="mt-2 border border-gray-200 rounded-lg max-h-40 overflow-y-auto">
                {searchResults.map(user => (
                  <div key={user.userId} className="flex items-center justify-between p-2 border-b border-gray-100 last:border-b-0">
                    <span className="font-mono text-gray-700">{user.userId}</span>
                    {currentUserData.friends.includes(user.userId) ? (
                      <span className="text-green-600 text-sm">Đã là bạn</span>
                    ) : currentUserData.sentRequests.includes(user.userId) ? (
                      <span className="text-yellow-600 text-sm">Đã gửi yêu cầu</span>
                    ) : currentUserData.receivedRequests.includes(user.userId) ? (
                      <span className="text-blue-600 text-sm">Đã nhận yêu cầu</span>
                    ) : (
                      <button
                        onClick={() => handleSendFriendRequest(user.userId)}
                        className="bg-blue-500 hover:bg-blue-600 text-white text-sm px-3 py-1 rounded-md transition duration-300"
                      >
                        Kết bạn
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
            {searchTerm && searchResults.length === 0 && (
              <p className="text-gray-500 text-sm mt-2">Không tìm thấy người dùng nào.</p>
            )}
          </div>

          {/* Lời mời kết bạn đã nhận */}
          <div className="mb-4">
            <h3 className="text-lg font-semibold mb-2 text-gray-700">Lời mời kết bạn đã nhận ({currentUserData.receivedRequests.length})</h3>
            {currentUserData.receivedRequests.length === 0 ? (
              <p className="text-gray-500 text-sm">Không có lời mời kết bạn nào.</p>
            ) : (
              <div className="border border-gray-200 rounded-lg max-h-40 overflow-y-auto">
                {currentUserData.receivedRequests.map(requesterId => (
                  <div key={requesterId} className="flex items-center justify-between p-2 border-b border-gray-100 last:border-b-0">
                    <span className="font-mono text-gray-700">{requesterId}</span>
                    <div className="flex space-x-2">
                      <button
                        onClick={() => handleAcceptFriendRequest(requesterId)}
                        className="bg-green-500 hover:bg-green-600 text-white text-sm px-3 py-1 rounded-md transition duration-300"
                      >
                        Chấp nhận
                      </button>
                      <button
                        onClick={() => handleRejectFriendRequest(requesterId)}
                        className="bg-red-500 hover:bg-red-600 text-white text-sm px-3 py-1 rounded-md transition duration-300"
                      >
                        Từ chối
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Bạn bè của tôi */}
          <div>
            <h3 className="text-lg font-semibold mb-2 text-gray-700">Bạn bè của tôi ({currentUserData.friends.length})</h3>
            {currentUserData.friends.length === 0 ? (
              <p className="text-gray-500 text-sm">Bạn chưa có người bạn nào.</p>
            ) : (
              <div className="border border-gray-200 rounded-lg max-h-40 overflow-y-auto">
                {currentUserData.friends.map(friendId => {
                  const status = getUserStatus(friendId);
                  return (
                    <div key={friendId} className="flex items-center p-2 border-b border-gray-100 last:border-b-0">
                      <span className={`w-3 h-3 rounded-full mr-2 ${status.color === 'text-green-500' ? 'bg-green-500' : 'bg-red-500'}`}></span>
                      <span className="font-mono text-gray-700">{friendId}</span>
                      <span className={`ml-2 text-xs ${status.color}`}>({status.status === 'online' ? 'Trực tuyến' : 'Ngoại tuyến'})</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-white mx-4 mt-4 rounded-lg shadow-inner" ref={messagesEndRef}>
        {messages.length === 0 ? (
          <div className="text-center text-gray-500 mt-10">Chưa có tin nhắn nào. Hãy là người đầu tiên gửi!</div>
        ) : (
          messages.map((msg) => {
            const senderStatus = getUserStatus(msg.senderId);
            return (
              <div
                key={msg.id}
                className={`flex ${msg.senderId === userId ? 'justify-end' : 'justify-start'}`}
              >
                <div className="flex flex-col">
                  {msg.type === 'message' && (
                    <div
                      className={`max-w-xs p-3 rounded-xl shadow-md ${
                        msg.senderId === userId
                          ? 'bg-blue-500 text-white rounded-br-none'
                          : 'bg-gray-200 text-gray-800 rounded-bl-none'
                      }`}
                    >
                      <div className="flex items-center mb-1">
                        <span className={`w-2 h-2 rounded-full mr-1 ${senderStatus.color === 'text-green-500' ? 'bg-green-500' : 'bg-red-500'}`}></span>
                        <p className="font-semibold text-sm">{msg.senderId === userId ? 'Bạn' : msg.senderId.substring(0, 8)}</p>
                      </div>
                      <p className="text-base">{msg.text}</p>
                      <div className="flex justify-between items-center mt-1">
                        {msg.timestamp && (
                          <span className="block text-xs opacity-75">
                            {new Date(msg.timestamp.toDate()).toLocaleTimeString()}
                          </span>
                        )}
                        {/* Like button and count */}
                        <button
                          onClick={() => handleLikeMessage(msg.id, msg.likes || [])}
                          className={`flex items-center text-xs ml-2 p-1 rounded-full ${
                            (msg.likes || []).includes(userId) ? 'text-red-500' : 'text-gray-500'
                          } hover:text-red-600 transition-colors duration-200`}
                          title={(msg.likes || []).includes(userId) ? 'Bỏ thích' : 'Thích'}
                        >
                          <svg className="w-4 h-4 mr-1" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" clipRule="evenodd"></path>
                          </svg>
                          <span>{(msg.likes || []).length > 0 ? (msg.likes || []).length : ''}</span>
                        </button>
                      </div>
                    </div>
                  )}
                  {msg.type === 'call' && (
                    <div
                      className={`max-w-xs p-3 rounded-xl shadow-md flex items-center ${
                        msg.senderId === userId
                          ? 'bg-green-500 text-white rounded-br-none'
                          : 'bg-yellow-500 text-gray-800 rounded-bl-none'
                      }`}
                    >
                      <svg className="w-6 h-6 mr-2" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M2 3a1 1 0 011-1h2.153a1 1 0 01.986.836l.74 4.435a1 1 0 01-.54 1.06l-1.548.774a11.037 11.037 0 006.103 6.103l.774-1.548a1 1 0 011.06-.54l4.435.74a1 1 0 01.836.986V17a1 1 0 01-1 1h-2C7.82 18 2 12.18 2 5V3z"></path>
                      </svg>
                      <span>
                        {msg.senderId === userId ? 'Bạn đã thực hiện cuộc gọi.' : `${msg.senderId.substring(0, 8)} đã thực hiện cuộc gọi.`}
                      </span>
                      {msg.timestamp && (
                        <span className="block text-xs text-right opacity-75 mt-1 ml-2">
                          {new Date(msg.timestamp.toDate()).toLocaleTimeString()}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Input and Button Area */}
      <div className="p-4 bg-white border-t border-gray-200 flex items-center space-x-3 mx-4 mb-4 rounded-lg shadow-lg">
        <input
          type="text"
          className="flex-1 p-3 border border-gray-300 rounded-full focus:outline-none focus:ring-2 focus:ring-blue-400"
          placeholder="Nhập tin nhắn của bạn..."
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          onKeyPress={(e) => {
            if (e.key === 'Enter') {
              handleSendMessage();
            }
          }}
        />
        <button
          onClick={() => handleSendMessage()}
          className="bg-blue-500 hover:bg-blue-600 text-white p-3 rounded-full shadow-md transition duration-300 ease-in-out transform hover:scale-105"
          title="Gửi tin nhắn"
        >
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
            <path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l.646-.279A6.002 6.002 0 0110 14a6.002 6.002 0 015.175 2.573l.646.279a1 1 0 001.169-1.409l-7-14z"></path>
          </svg>
        </button>
        <button
          onClick={handleCall}
          className="bg-green-500 hover:bg-green-600 text-white p-3 rounded-full shadow-md transition duration-300 ease-in-out transform hover:scale-105"
          title="Thực hiện cuộc gọi (mô phỏng)"
        >
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
            <path d="M2 3a1 1 0 011-1h2.153a1 1 0 01.986.836l.74 4.435a1 1 0 01-.54 1.06l-1.548.774a11.037 11.037 0 006.103 6.103l.774-1.548a1 1 0 011.06-.54l4.435.74a1 1 0 01.836.986V17a1 1 0 01-1 1h-2C7.82 18 2 12.18 2 5V3z"></path>
          </svg>
        </button>
      </div>
    </div>
  );
}

export default App;
