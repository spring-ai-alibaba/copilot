import { useEffect } from "react"

const useRag = () => {
    useEffect(() => {
        const ragTime = setInterval(() => {
            // useRag interval
        }, 1000)
        return () => clearInterval(ragTime)
    }, [])
}