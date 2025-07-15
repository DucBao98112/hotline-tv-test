import React, { useState, useEffect } from "react";
import { FiPhone, FiVideo, FiMessageSquare, FiUsers, FiGlobe, FiMoon, FiSun, FiSend, FiSmile, FiPaperclip, FiLogIn, FiUserPlus, FiSearch, FiUserCheck } from "react-icons/fi";

const languages = [
  { code: "vi", name: "Vietnamese" },
  { code: "en", name: "English" },
  { code: "zh", name: "Chinese" },
  { code: "ja", name: "Japanese" },
  { code: "ko", name: "Korean" }
];

const contacts = [
  { id: 1, name: "John Doe", phone: "+1234567890", status: "online", lastSeen: "just now", avatar: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e" },
  { id: 2, name: "Jane Smith", phone: "+1987654321", status: "offline", lastSeen: "2 hours ago", avatar: "https://images.unsplash.com/photo-1438761681033-6461ffad8d80" },
  { id: 3, name: "Alex Johnson", phone: "+1122334455", status: "busy", lastSeen: "5 mins ago", avatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e" },
  { id: 4, name: "Sarah Wilson", phone: "+1555666777", status: "online", lastSeen: "just now", avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb" },
  { id: 5, name: "Mike Brown", phone: "+1999888777", status: "away", lastSeen: "30 mins ago", avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d" }
];

const initialMessages = [
  { id: 1, senderId: 1, text: "Hey there!", timestamp: "09:30" },
  { id: 2, senderId: 2, text: "Hi! How are you?", timestamp: "09:31" },
  { id: 3, senderId: 1, text: "I'm doing great, thanks!", timestamp: "09:32" }
];

const App = () => {
  const [darkMode, setDarkMode] = useState(false);
  const [selectedChat, setSelectedChat] = useState(null);
  const [messages, setMessages] = useState(initialMessages);
  const [newMessage, setNewMessage] = useState("");
  const [searchPhone, setSearchPhone] = useState("");
  const [filteredContacts, setFilteredContacts] = useState(contacts);
  const [showAddFriend, setShowAddFriend] = useState(false);
  const [newFriendPhone, setNewFriendPhone] = useState("");
  const [showRegister, setShowRegister] = useState(true);
  const [registerData, setRegisterData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: ""
  });

  useEffect(() => {
    const filtered = contacts.filter(contact =>
      contact.phone.includes(searchPhone) || contact.name.toLowerCase().includes(searchPhone.toLowerCase())
    );
    setFilteredContacts(filtered);
  }, [searchPhone]);

  const handleRegister = (e) => {
    e.preventDefault();
    if (registerData.password === registerData.confirmPassword) {
      setShowRegister(false);
    } else {
      alert("Passwords do not match!");
    }
  };

  const handleAddFriend = () => {
    if (newFriendPhone.trim()) {
      alert("Friend request sent to " + newFriendPhone);
      setNewFriendPhone("");
      setShowAddFriend(false);
    }
  };

  const toggleDarkMode = () => {
    setDarkMode(!darkMode);
  };

  const handleSendMessage = () => {
    if (newMessage.trim()) {
      const newMsg = {
        id: messages.length + 1,
        senderId: 1,
        text: newMessage,
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
      };
      setMessages([...messages, newMsg]);
      setNewMessage("");
    }
  };

  return (
    <div className={`min-h-screen ${darkMode ? "bg-gray-900 text-white" : "bg-gray-50 text-gray-900"}`}>
      {showRegister ? (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
          <div className="max-w-md w-full space-y-8">
            <div>
              <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                Create your account
              </h2>
            </div>
            <form className="mt-8 space-y-6" onSubmit={handleRegister}>
              <div className="rounded-md shadow-sm -space-y-px">
                <div>
                  <input
                    type="text"
                    required
                    className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-t-md focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                    placeholder="Username"
                    value={registerData.username}
                    onChange={(e) => setRegisterData({...registerData, username: e.target.value})}
                  />
                </div>
                <div>
                  <input
                    type="email"
                    required
                    className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                    placeholder="Email address"
                    value={registerData.email}
                    onChange={(e) => setRegisterData({...registerData, email: e.target.value})}
                  />
                </div>
                <div>
                  <input
                    type="password"
                    required
                    className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                    placeholder="Password"
                    value={registerData.password}
                    onChange={(e) => setRegisterData({...registerData, password: e.target.value})}
                  />
                </div>
                <div>
                  <input
                    type="password"
                    required
                    className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-b-md focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                    placeholder="Confirm password"
                    value={registerData.confirmPassword}
                    onChange={(e) => setRegisterData({...registerData, confirmPassword: e.target.value})}
                  />
                </div>
              </div>
              <div>
                <button
                  type="submit"
                  className="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                >
                  Register
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : (
        <div>
          <nav className={`fixed w-full top-0 ${darkMode ? "bg-gray-800" : "bg-white"} shadow-lg z-50`}>
            <div className="container mx-auto px-4 py-3 flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <img src="https://cdn-icons-png.flaticon.com/512/2665/2665393.png" alt="Logo" className="h-8 w-8 rounded-full" />
                <h1 className="text-xl font-bold">CallChat App</h1>
              </div>
              <div className="flex items-center space-x-4">
                <button
                  onClick={toggleDarkMode}
                  className="p-2 rounded-full hover:bg-gray-200 dark:hover:bg-gray-700"
                >
                  {darkMode ? <FiSun className="w-5 h-5" /> : <FiMoon className="w-5 h-5" />}
                </button>
              </div>
            </div>
          </nav>

          <div className="container mx-auto px-4 pt-20 pb-4 flex gap-4 h-screen">
            <aside className={`w-1/4 ${darkMode ? "bg-gray-800" : "bg-white"} rounded-lg shadow-lg p-4 overflow-y-auto`}>
              <div className="mb-4 space-y-2">
                <div className="flex items-center space-x-2">
                  <input
                    type="text"
                    value={searchPhone}
                    onChange={(e) => setSearchPhone(e.target.value)}
                    placeholder="Search by phone or name"
                    className={`flex-1 px-3 py-2 rounded-lg ${darkMode ? "bg-gray-700" : "bg-gray-100"}`}
                  />
                  <button
                    onClick={() => setShowAddFriend(true)}
                    className="p-2 rounded-lg bg-blue-500 text-white hover:bg-blue-600"
                  >
                    <FiUserPlus className="w-5 h-5" />
                  </button>
                </div>

                {showAddFriend && (
                  <div className="p-3 bg-gray-100 dark:bg-gray-700 rounded-lg">
                    <div className="flex items-center space-x-2">
                      <input
                        type="text"
                        value={newFriendPhone}
                        onChange={(e) => setNewFriendPhone(e.target.value)}
                        placeholder="Enter phone number"
                        className={`flex-1 px-3 py-2 rounded-lg ${darkMode ? "bg-gray-600" : "bg-white"}`}
                      />
                      <button
                        onClick={handleAddFriend}
                        className="p-2 rounded-lg bg-green-500 text-white hover:bg-green-600"
                      >
                        <FiUserCheck className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                )}
              </div>

              <div className="flex justify-between items-center mb-4">
                <h2 className="text-lg font-semibold">Contacts</h2>
                <div className="flex items-center space-x-2">
                  <FiUsers className="w-5 h-5" />
                  <span className="text-sm text-gray-500">{filteredContacts.length}</span>
                </div>
              </div>

              <div className="space-y-2">
                {filteredContacts.map((contact) => (
                  <div
                    key={contact.id}
                    onClick={() => setSelectedChat(contact)}
                    className={`flex items-center p-3 rounded-lg cursor-pointer transition-colors duration-200 ${
                      selectedChat?.id === contact.id
                        ? darkMode
                          ? "bg-gray-700"
                          : "bg-gray-100"
                        : "hover:bg-gray-100 dark:hover:bg-gray-700"
                    }`}
                  >
                    <div className="relative">
                      <img src={contact.avatar} alt={contact.name} className="w-12 h-12 rounded-full object-cover" />
                      <span className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white ${
                        contact.status === "online" ? "bg-green-500" :
                        contact.status === "busy" ? "bg-red-500" :
                        contact.status === "away" ? "bg-yellow-500" : "bg-gray-500"
                      }`}></span>
                    </div>
                    <div className="ml-3 flex-1">
                      <div className="flex justify-between items-center">
                        <p className="font-medium">{contact.name}</p>
                        <span className="text-xs text-gray-500">{contact.lastSeen}</span>
                      </div>
                      <p className="text-sm text-gray-500">{contact.phone}</p>
                      <p className="text-sm text-gray-500">{contact.status}</p>
                    </div>
                  </div>
                ))}
              </div>
            </aside>

            <main className={`flex-1 ${darkMode ? "bg-gray-800" : "bg-white"} rounded-lg shadow-lg p-4 flex flex-col`}>
              {selectedChat ? (
                <div className="h-full flex flex-col">
                  <div className="flex items-center justify-between pb-4 border-b dark:border-gray-700">
                    <div className="flex items-center">
                      <div className="relative">
                        <img src={selectedChat.avatar} alt={selectedChat.name} className="w-12 h-12 rounded-full object-cover" />
                        <span className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white ${
                          selectedChat.status === "online" ? "bg-green-500" :
                          selectedChat.status === "busy" ? "bg-red-500" :
                          selectedChat.status === "away" ? "bg-yellow-500" : "bg-gray-500"
                        }`}></span>
                      </div>
                      <div className="ml-3">
                        <h3 className="font-semibold">{selectedChat.name}</h3>
                        <p className="text-sm text-gray-500">{selectedChat.phone}</p>
                      </div>
                    </div>
                    <div className="flex space-x-2">
                      <button className="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors duration-200">
                        <FiPhone className="w-5 h-5" />
                      </button>
                      <button className="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors duration-200">
                        <FiVideo className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                  <div className="flex-1 overflow-y-auto py-4 space-y-4">
                    {messages.map((message) => (
                      <div key={message.id} className={`flex ${message.senderId === 1 ? "justify-end" : "justify-start"}`}>
                        <div className={`max-w-[70%] rounded-lg p-3 ${message.senderId === 1 ? "bg-blue-500 text-white" : darkMode ? "bg-gray-700" : "bg-gray-100"}`}>
                          <p>{message.text}</p>
                          <span className="text-xs opacity-70 mt-1 block">{message.timestamp}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="pt-4 border-t dark:border-gray-700">
                    <div className="flex items-center space-x-2">
                      <button className="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors duration-200">
                        <FiPaperclip className="w-5 h-5" />
                      </button>
                      <input
                        type="text"
                        value={newMessage}
                        onChange={(e) => setNewMessage(e.target.value)}
                        onKeyPress={(e) => e.key === "Enter" && handleSendMessage()}
                        placeholder="Type a message"
                        className={`flex-1 px-4 py-2 rounded-lg ${darkMode ? "bg-gray-700" : "bg-gray-100"} focus:outline-none focus:ring-2 focus:ring-blue-500`}
                      />
                      <button className="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors duration-200">
                        <FiSmile className="w-5 h-5" />
                      </button>
                      <button
                        onClick={handleSendMessage}
                        className="p-2 rounded-full bg-blue-500 text-white hover:bg-blue-600 transition-colors duration-200"
                      >
                        <FiSend className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="h-full flex items-center justify-center">
                  <p className="text-gray-500">Select a chat to start messaging</p>
                </div>
              )}
            </main>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;