(ns tranjlator.sample-data)

(def sample-source {:topic "Original" :language "English" :content "Hi" :original-sha "XYZ"
                    :content-sha "XYZ" :user-name "bob"})

(def sample-translation {:topic "German" :language "German" :content "Guten Tag"
                         :original-sha "XYZ" :content-sha "ABC" :user-name "bob"})

(def sample-users ["Eric" "Colin" "Brian" "Rick"])
