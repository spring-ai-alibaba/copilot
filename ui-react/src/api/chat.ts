
export const uploadImage = async (file: File) => {
  // 使用FileReader将文件转换为Base64
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    
    reader.onload = () => {
      const base64String = reader.result as string;
      resolve(base64String);
    };
    
    reader.onerror = () => {
      reject(new Error('Failed to convert image to Base64'));
    };

    reader.readAsDataURL(file);
  });
};
