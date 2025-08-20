import { Code } from "lucide-react";
import { motion } from "framer-motion";

export const Logo = () => (
  <div className="group flex items-center gap-3">
    <motion.div
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
      className="h-24 w-24 flex justify-center items-center relative p-2 rounded-3xl bg-gradient-to-br from-blue-500 to-purple-500 overflow-hidden"
    >
      <motion.div
        className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent"
        animate={{ x: ["-200%", "200%"] }}
        transition={{ repeat: Infinity, duration: 2, ease: "linear" }}
      />
      <Code size={50} className=" text-white relative z-10" />
    </motion.div>
  </div>
);
