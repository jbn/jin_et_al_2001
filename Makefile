all: index.ipynb
	ipython nbconvert --to html --template full index.ipynb
	python fix_html.py
