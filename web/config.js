// Firebase web config for the staff dashboard. These values are public by design (Firebase
// docs): access is controlled by Authentication and firebase/database.rules.json, not by this file.
// apiKey and appId come from Firebase console > Project settings > Your apps > "Campus Find Staff".
export const firebaseConfig = {
  apiKey: 'AIzaSyA-QkFgCcDgyVmLkfIQm-SqjpZpqmyqplI',
  authDomain: 'campus-find-86d58.firebaseapp.com',
  databaseURL: 'https://campus-find-86d58-default-rtdb.firebaseio.com',
  projectId: 'campus-find-86d58',
  appId: '1:1091688477081:web:e2e37388a71ad3bb7b6308',
};

// Opened from localhost (testing), the dashboard uses the local Firebase emulators instead.
export const useEmulators = ['localhost', '127.0.0.1'].includes(location.hostname);
