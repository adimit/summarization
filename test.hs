{-# LANGUAGE NamedFieldPuns #-}
module Main where

import Data.Maybe (mapMaybe)
import System.Directory (getDirectoryContents, doesFileExist, doesDirectoryExist)
import qualified Data.Map.Lazy as M
import Data.List (foldl')
import Control.Monad (liftM)
import Text.Printf
import System.FilePath ((</>))

data Document = Document { file :: FilePath, text :: String, terms :: [Term] }
data Term = Term String Double

instance Show Document where
  show Document {file, text, terms} = "Document: " ++ file ++ "\n" ++ text ++ "\n++++++++++++Terms+++++++++\n" ++ show terms

instance Show Term where
  show (Term surface tfidf) = surface ++ " (" ++ printf "%.4f" tfidf ++ ")"

type TermFrequencies = M.Map String Int

testDirectory :: String
testDirectory = "test"

tokenize :: String -> [String]
tokenize = words . concatMap adjustPunct
  where adjustPunct x | x `elem` "?!.," = ' ':x:" "
        adjustPunct '\'' = " '"
        adjustPunct x = [x]

scoreDocument :: Int -> TermFrequencies -> TermFrequencies -> String -> FilePath -> Document
scoreDocument n collectionFreqs docFreqs documentContent filePath = Document filePath documentContent terms
  where terms = mapMaybe (uncurry $ computeTFIDF n collectionFreqs) . M.toList $ docFreqs

computeTFIDF :: Int -> TermFrequencies -> String -> Int -> Maybe Term
computeTFIDF n collectionFreqs term termfreq = do
  df <- M.lookup term collectionFreqs
  let idf = fromIntegral n / fromIntegral df
      tfidf = fromIntegral termfreq * logBase 10 idf
  return $ Term term tfidf

getFileOrDirectoryRecursively :: FilePath -> IO [FilePath]
getFileOrDirectoryRecursively fp = do
  fileExists <- doesFileExist fp
  if fileExists
    then return [fp]
    else do
    isDirectory <- doesDirectoryExist fp
    if isDirectory
      then liftM (filter (not . (`elem` [".", ".."]))) (getDirectoryContents fp)
           >>= fmap concat . mapM (getFileOrDirectoryRecursively . (fp </>))
      else return []

main :: IO ()
main = do
  files <- getFileOrDirectoryRecursively testDirectory
  fileContents <- mapM readFile files
  let n = length files
      documentFreqs = map (foldl' (\m word -> M.insertWith (+) word 1 m) M.empty . tokenize) fileContents
      collectionFreqs = foldl' (\colFreqs term -> M.insertWith (+) term 1 colFreqs) M.empty . map fst $ concatMap M.toList documentFreqs
      documentScorer = scoreDocument n collectionFreqs
      scoredDocuments = zipWith3 documentScorer documentFreqs fileContents files
  mapM_ print scoredDocuments
