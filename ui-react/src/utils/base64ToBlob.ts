const base64ToBlob = ({
    b64data = "",
    contentType = "",
    sliceSize = 512,
  } = {}) => {
    return new Promise((resolve, reject) => {
      let byteCharacters = atob(b64data);
      let byteArrays = [];
      for (let offset = 0; offset < byteCharacters.length; offset += sliceSize) {
        let slice = byteCharacters.slice(offset, offset + sliceSize);
        let byteNumbers = [];
        for (let i = 0; i < slice.length; i++) {
          byteNumbers.push(slice.charCodeAt(i));
        }
        byteArrays.push(new Uint8Array(byteNumbers));
      }
      let result = new Blob(byteArrays, {
        type: contentType,
      });
      result = Object.assign(result, {
        preview: URL.createObjectURL(result),
        name: `XXX.png`,
      });
      resolve(result);
    });
  };

export default base64ToBlob;
